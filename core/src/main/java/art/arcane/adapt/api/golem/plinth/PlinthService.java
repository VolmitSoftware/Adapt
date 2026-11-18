package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.event.world.WorldLifecycleEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class PlinthService extends TickedObject {
  private static final Id LODESTONE = Id.minecraft("lodestone");
  private static final Id COMPASS = Id.minecraft("compass");
  public static final String LOADING_ADAPTATION = InfusionRecipe.RUNIC_AUGMENTS;
  public static final int LOADING_LEVEL = 1;
  static final long PREVIEW_THROTTLE_MILLIS = 1_000L;
  private static final int SAVE_ATTEMPTS = 20;
  private static final int SAVE_RETRY_TICKS = 2;
  private static final int REVALIDATE_DELAY_TICKS = 1;
  private static final int PULSE_TICKS = 1;
  private static final double RESULT_HEIGHT = 1.25D;
  private static final double CENTER = 0.5D;
  private static final Runnable NOTHING = () -> {
  };

  private final PlinthPlatform platform;
  private final Supplier<PlinthSettings> settings;
  private final Supplier<PlinthHost> host;
  private final PlinthIndex index = new PlinthIndex();
  private final PlinthRenderer renderer;
  private final Map<PlinthSpot, InfusionRitual> ritualsByCenter = new ConcurrentHashMap<>();
  private final Map<UUID, InfusionRitual> ritualsByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, Preview> previewsByViewer = new ConcurrentHashMap<>();

  PlinthService(PlinthPlatform platform, Supplier<PlinthSettings> settings, Supplier<PlinthHost> host) {
    super("golemancy", "golem-plinths");
    this.platform = Objects.requireNonNull(platform);
    this.settings = Objects.requireNonNull(settings);
    this.host = Objects.requireNonNull(host);
    this.renderer = new PlinthRenderer(platform);
  }

  public static PlinthService create(Supplier<PlinthSettings> settings, Supplier<PlinthHost> host) {
    return new PlinthService(new LivePlinthPlatform(host), settings, host);
  }

  public void indexLoadedChunks() {
    for (WorldRef world : AdaptCore.platform().host().worlds()) {
      for (ChunkPos chunk : world.loadedChunks()) {
        load(world, chunk.x(), chunk.z());
      }
    }
  }

  public boolean isPlinthBlock(BlockRef block) {
    Set<Id> pedestals = settings.get().pedestals();
    return PlinthStructure.isPart(block.type(), pedestals)
        && PlinthStructure.locate(blocks(block.world()), PlinthSpot.of(block), pedestals).isPresent();
  }

  public void shutdown() {
    unregister();
    for (InfusionRitual ritual : List.copyOf(ritualsByCenter.values())) {
      if (ritual.finish()) {
        forget(ritual);
      }
    }
    previewsByViewer.clear();
    renderer.shutdown();
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    BlockRef block = e.clickedBlock();
    if (e.protectionProbe() || e.action() != InteractAction.RIGHT_CLICK_BLOCK || block == null) {
      return;
    }
    PlinthSettings current = settings.get();
    Id type = block.type();
    PlinthHost activeHost = host.get();
    if (!PlinthStructure.isPart(type, current.pedestals()) || activeHost == null) {
      return;
    }
    PlayerRef player = e.player();
    PlayerInventoryRef inventory = player.inventory();
    ItemRef held = inventory.mainHand();
    boolean lodestone = LODESTONE.equals(type);
    WorldRef world = block.world();
    if (platform.isCrook(held)) {
      if (lodestone) {
        crookClicked(e, player, inventory, world, PlinthSpot.of(block), activeHost, current);
      }
      return;
    }
    if (!player.isSneaking() || (lodestone && holdsCompass(inventory))) {
      return;
    }
    Optional<PlinthStructure.PlinthTarget> target = PlinthStructure.locate(blocks(world), PlinthSpot.of(block), current.pedestals());
    if (target.isEmpty() || activeHost.level(player, LOADING_ADAPTATION) < LOADING_LEVEL) {
      return;
    }
    e.setCancelled(true);
    if (e.hand() == Hand.MAIN_HAND) {
      load(player, world, target.get(), held, activeHost);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    blocksChanging(List.of(e.block()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockExplodeEvent e) {
    blocksChanging(e.blocks());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityExplodeEvent e) {
    blocksChanging(e.blocks());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPistonEvent e) {
    blocksChanging(e.blocks());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityChangeBlockEvent e) {
    if (!PlinthStructure.isPart(e.to().type(), settings.get().pedestals())) {
      blocksChanging(List.of(e.block()));
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    previewsByViewer.remove(e.player().uuid());
    InfusionRitual ritual = ritualsByPlayer.get(e.player().uuid());
    if (ritual != null) {
      onPlinthThread(ritual, () -> abort(ritual, false));
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(ChunkEvent e) {
    ChunkPos chunk = e.chunk();
    UUID worldId = e.world().uid();
    long chunkKey = GolemRoster.chunkKey(chunk.x(), chunk.z());
    if (e.phase() == ChunkEvent.Phase.UNLOAD) {
      forget(index.unload(worldId, chunkKey));
      return;
    }
    if (e.newChunk()) {
      index.completeLoad(worldId, chunkKey, index.beginLoad(worldId, chunkKey), Map.of());
      return;
    }
    load(e.world(), chunk.x(), chunk.z());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(WorldLifecycleEvent e) {
    if (e.phase() == WorldLifecycleEvent.Phase.UNLOAD) {
      forget(index.unloadWorld(e.world().uid()));
    }
  }

  private void crookClicked(PlayerInteractEvent e, PlayerRef player, PlayerInventoryRef inventory, WorldRef world, PlinthSpot center, PlinthHost activeHost,
                            PlinthSettings current) {
    boolean offHand = e.hand() != Hand.MAIN_HAND;
    if ((offHand && isCompass(inventory.offHand()))
        || !PlinthStructure.valid(blocks(world), center, current.pedestals())
        || activeHost.level(player, LOADING_ADAPTATION) < LOADING_LEVEL) {
      return;
    }
    e.setCancelled(true);
    if (e.hand() == Hand.MAIN_HAND) {
      begin(player, world, center, activeHost, current);
    }
  }

  private void load(PlayerRef player, WorldRef world, PlinthStructure.PlinthTarget target, ItemRef held, PlinthHost activeHost) {
    PlinthSpot spot = target.spot();
    Loc at = spot.center(world);
    if (!index.isReady(spot.world(), spot.chunkKey()) || !platform.owns(at)) {
      platform.tell(player, true, GolemancyMessages.PLINTH_LOADING);
      return;
    }
    if (!activeHost.canStore(player, LOADING_ADAPTATION, at)) {
      platform.tell(player, true, GolemancyMessages.PLINTH_PROTECTED);
      return;
    }
    PlinthSlotItem current = platform.read(world, spot);
    if (held == null || held.isEmpty()) {
      if (current != null) {
        retrieve(player, world, target, current, activeHost);
      }
      return;
    }
    if (current != null) {
      platform.tell(player, true, GolemancyMessages.PLINTH_OCCUPIED);
      return;
    }
    ItemRef single = platform.takeOne(player);
    PlinthSlotItem stored = PlinthSlotItem.of(UUID.randomUUID(), single);
    platform.write(world, spot, stored);
    platform.saveData(player);
    index.put(spot, stored);
    renderer.spawn(world, spot, single);
    abortCovering(spot);
    platform.effect(PlinthEffect.LOADED, at);
    previewCost(player, world, target.center(), activeHost);
  }

  private void retrieve(PlayerRef player, WorldRef world, PlinthStructure.PlinthTarget target, PlinthSlotItem stored, PlinthHost activeHost) {
    PlinthSpot spot = target.spot();
    ItemRef item = stored.item();
    if (item == null && !stored.lost()) {
      platform.tell(player, true, GolemancyMessages.PLINTH_UNREADABLE);
      return;
    }
    if (!release(world, spot, stored)) {
      return;
    }
    Loc at = spot.center(world);
    platform.effect(PlinthEffect.RETRIEVED, at);
    previewCost(player, world, target.center(), activeHost);
    if (item == null) {
      return;
    }
    saveThen(world, List.of(spot), at, () -> handBack(player, at, item), NOTHING, "the item taken from the Animus Plinth at " + describe(spot));
  }

  private void handBack(PlayerRef player, Loc at, ItemRef item) {
    if (player.isOnline() && platform.owns(player)) {
      platform.give(player, item);
    } else {
      platform.drop(at, item);
    }
  }

  private void begin(PlayerRef player, WorldRef world, PlinthSpot center, PlinthHost activeHost, PlinthSettings current) {
    long now = platform.nowMillis();
    if (!slotsReady(world, center)) {
      platform.tell(player, true, GolemancyMessages.PLINTH_LOADING);
      return;
    }
    if (busy(ritualsByCenter.get(center), now) || busy(ritualsByPlayer.get(player.uuid()), now)) {
      platform.tell(player, true, GolemancyMessages.PLINTH_BUSY);
      return;
    }
    Map<PlinthSlot, PlinthSlotItem> slots = snapshot(center);
    Assessment assessment = assess(player, world, center, slots, activeHost, current);
    if (assessment.verdict() != Verdict.READY) {
      deny(player, assessment);
      return;
    }
    InfusionRitual ritual = new InfusionRitual(player, center, assessment.infusion(), slots, now);
    ritualsByCenter.put(center, ritual);
    ritualsByPlayer.put(player.uuid(), ritual);
    platform.effect(PlinthEffect.STARTED, center.center(world));
    showCost(player, center, assessment);
    schedulePulse(world, ritual);
  }

  private void schedulePulse(WorldRef world, InfusionRitual ritual) {
    if (!platform.runAt(ritual.center().center(world), () -> pulse(world, ritual), PULSE_TICKS)) {
      abort(ritual, true);
    }
  }

  private void pulse(WorldRef world, InfusionRitual ritual) {
    if (ritual.isFinished()) {
      return;
    }
    ritual.pulsed(platform.nowMillis());
    PlayerRef player = ritual.player();
    PlinthSpot center = ritual.center();
    boolean online = player.isOnline();
    boolean present = online && player.isValid() && !player.isDead() && platform.owns(player);
    Loc playerAt = present ? player.location() : null;
    boolean sameWorld = playerAt != null && world.equals(playerAt.world());
    double distanceSquared = sameWorld ? center.distanceSquared(playerAt) : Double.POSITIVE_INFINITY;
    boolean structure = PlinthStructure.valid(blocks(world), center, settings.get().pedestals());
    Optional<InfusionRitual.Interruption> interruption = ritual.check(online, sameWorld, distanceSquared, structure,
        slot -> index.get(center.slot(slot)).orElse(null));
    if (interruption.isPresent()) {
      abort(ritual, interruption.get() != InfusionRitual.Interruption.LOGGED_OUT);
      return;
    }
    ritual.advance();
    animate(world, ritual);
    if (ritual.due()) {
      complete(world, ritual);
      return;
    }
    schedulePulse(world, ritual);
  }

  private void animate(WorldRef world, InfusionRitual ritual) {
    PlinthSpot center = ritual.center();
    double progress = ritual.progress();
    int elapsed = ritual.elapsed();
    renderer.animate(center, InfusionVortex.centerLift(progress), InfusionVortex.centerSpin(progress));
    for (int pedestal = 0; pedestal < PlinthSlot.PEDESTALS.size(); pedestal++) {
      renderer.animate(center.slot(PlinthSlot.PEDESTALS.get(pedestal)), InfusionVortex.pedestalLift(progress, elapsed, pedestal), PlinthRenderer.IDLE_DEGREES_PER_TICK);
    }
    platform.swirl(center.center(world), ritual);
  }

  private void complete(WorldRef world, InfusionRitual ritual) {
    if (!ritual.finish()) {
      return;
    }
    forget(ritual);
    PlayerRef player = ritual.player();
    PlinthSpot center = ritual.center();
    PlinthHost activeHost = host.get();
    if (activeHost == null || !PlinthStructure.valid(blocks(world), center, settings.get().pedestals()) || !storedUnchanged(world, ritual)) {
      interrupted(world, ritual, true);
      return;
    }
    Assessment assessment = assess(player, world, center, ritual.slots(), activeHost, settings.get());
    if (assessment.verdict() != Verdict.READY) {
      interrupted(world, ritual, false);
      deny(player, assessment);
      return;
    }
    Infusion infusion = assessment.infusion();
    Optional<PlinthCharge> charge = assessment.levels() <= 0 ? Optional.of(PlinthCharge.FREE)
        : activeHost.chargeLevels(player, infusion.unlockAdaptation(), assessment.levels());
    if (charge.isEmpty()) {
      interrupted(world, ritual, false);
      deny(player, assessment.withVerdict(Verdict.NOT_ENOUGH_LEVELS));
      return;
    }
    Map<PlinthSpot, PlinthSlotItem> removed = new LinkedHashMap<>();
    for (Map.Entry<PlinthSlot, PlinthSlotItem> entry : ritual.slots().entrySet()) {
      PlinthSpot spot = center.slot(entry.getKey());
      if (!take(world, spot, entry.getValue())) {
        AdaptLog.warn("The Animus Plinth item at " + describe(spot) + " changed while its infusion completed; the infusion was cancelled and its items restored.");
        restore(world, removed);
        resync(world, spot);
        refund(player, charge.get(), false);
        interrupted(world, ritual, true);
        return;
      }
      removed.put(spot, entry.getValue());
    }
    Loc at = center.center(world);
    saveThen(world, List.copyOf(removed.keySet()), at, () -> deliver(world, ritual, infusion, charge.get()), () -> {
      restore(world, removed);
      refund(player, charge.get(), true);
    }, "the infusion at the Animus Plinth at " + describe(center));
  }

  private void deliver(WorldRef world, InfusionRitual ritual, Infusion infusion, PlinthCharge charge) {
    PlayerRef player = ritual.player();
    PlinthSpot center = ritual.center();
    Loc at = new Loc(world, center.x() + CENTER, center.y() + RESULT_HEIGHT, center.z() + CENTER);
    platform.popResult(at, infusion.resultItem().get());
    charge.settle();
    platform.collide(center.center(world), ritual.tint());
    platform.tell(player, false, GolemancyMessages.PLINTH_COMPLETE);
    PlinthHost activeHost = host.get();
    if (activeHost != null) {
      activeHost.infused(player, infusion, at);
    }
  }

  private void refund(PlayerRef player, PlinthCharge charge, boolean announce) {
    if (platform.owns(player)) {
      charge.refund();
    } else {
      platform.runEntity(player, charge::refund, 0);
    }
    if (announce) {
      platform.tell(player, true, GolemancyMessages.PLINTH_FAILED);
    }
  }

  private void restore(WorldRef world, Map<PlinthSpot, PlinthSlotItem> removed) {
    Set<Id> pedestals = settings.get().pedestals();
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : removed.entrySet()) {
      PlinthSpot spot = entry.getKey();
      PlinthSlotItem item = entry.getValue();
      Id type = platform.blockType(world, spot.x(), spot.y(), spot.z());
      boolean attached = type == null || PlinthStructure.isPart(type, pedestals);
      if (!attached || platform.read(world, spot) != null) {
        platform.drop(spot.center(world), item.item());
        continue;
      }
      platform.write(world, spot, item);
      if (index.put(spot, item)) {
        renderer.spawn(world, spot, item.item());
      }
    }
  }

  private void resync(WorldRef world, PlinthSpot spot) {
    PlinthSlotItem stored = platform.read(world, spot);
    ItemRef item = stored == null ? null : stored.item();
    if (stored == null || stored.lost()) {
      index.remove(spot);
      renderer.despawn(spot);
      return;
    }
    if (!index.put(spot, stored)) {
      return;
    }
    if (item == null) {
      renderer.despawn(spot);
    } else {
      renderer.spawn(world, spot, item);
    }
  }

  private void abort(InfusionRitual ritual, boolean announce) {
    if (!ritual.finish()) {
      return;
    }
    forget(ritual);
    WorldRef world = platform.world(ritual.center().world());
    if (world != null) {
      interrupted(world, ritual, false);
    }
    if (announce) {
      platform.tell(ritual.player(), true, GolemancyMessages.PLINTH_INTERRUPTED);
    }
  }

  private void interrupted(WorldRef world, InfusionRitual ritual, boolean announce) {
    PlinthSpot center = ritual.center();
    for (PlinthSlot slot : PlinthSlot.values()) {
      renderer.rest(center.slot(slot));
    }
    platform.fizzle(center.center(world), ritual);
    if (announce) {
      platform.tell(ritual.player(), true, GolemancyMessages.PLINTH_INTERRUPTED);
    }
  }

  private void forget(InfusionRitual ritual) {
    ritualsByCenter.remove(ritual.center(), ritual);
    ritualsByPlayer.remove(ritual.player().uuid(), ritual);
  }

  private boolean busy(InfusionRitual ritual, long now) {
    if (ritual == null) {
      return false;
    }
    if (ritual.isFinished()) {
      forget(ritual);
      return false;
    }
    if (!ritual.stale(now)) {
      return true;
    }
    onPlinthThread(ritual, () -> abort(ritual, false));
    return false;
  }

  private void onPlinthThread(InfusionRitual ritual, Runnable task) {
    WorldRef world = platform.world(ritual.center().world());
    if (world == null) {
      lose(ritual);
      return;
    }
    Loc at = ritual.center().center(world);
    if (platform.owns(at)) {
      task.run();
    } else if (!platform.runAt(at, task, 0)) {
      lose(ritual);
    }
  }

  private void lose(InfusionRitual ritual) {
    ritual.finish();
    forget(ritual);
  }

  private void abortCovering(PlinthSpot spot) {
    for (InfusionRitual ritual : List.copyOf(ritualsByCenter.values())) {
      if (ritual.covers(spot)) {
        abort(ritual, true);
      }
    }
  }

  private Assessment assess(PlayerRef player, WorldRef world, PlinthSpot center, Map<PlinthSlot, PlinthSlotItem> slots, PlinthHost activeHost, PlinthSettings current) {
    // An item that cannot be read could be anything, so no recipe is matched around it and no infusion consumes it.
    for (PlinthSlotItem slot : slots.values()) {
      if (!slot.readable()) {
        return new Assessment(Verdict.NO_RECIPE, null, 0, 0);
      }
    }
    Optional<Infusion> match = current.recipes().match(readable(slots.get(PlinthSlot.CENTER)), pedestalItems(slots));
    if (match.isEmpty()) {
      return new Assessment(Verdict.NO_RECIPE, null, 0, 0);
    }
    Infusion infusion = match.get();
    int levels = InfusionRitual.levelCost(infusion, player.gameMode() == GameMode.CREATIVE);
    int have = player.level();
    if (activeHost.level(player, infusion.unlockAdaptation()) < infusion.unlockLevel()) {
      return new Assessment(Verdict.LOCKED, infusion, levels, have);
    }
    if (!activeHost.canInfuse(player, infusion.unlockAdaptation(), center.center(world))) {
      return new Assessment(Verdict.PROTECTED, infusion, levels, have);
    }
    if (have < levels) {
      return new Assessment(Verdict.NOT_ENOUGH_LEVELS, infusion, levels, have);
    }
    return new Assessment(Verdict.READY, infusion, levels, have);
  }

  private void previewCost(PlayerRef player, WorldRef world, PlinthSpot center, PlinthHost activeHost) {
    showCost(player, center, assess(player, world, center, snapshot(center), activeHost, settings.get()));
  }

  private void showCost(PlayerRef player, PlinthSpot center, Assessment assessment) {
    if (assessment.verdict() != Verdict.READY && assessment.verdict() != Verdict.NOT_ENOUGH_LEVELS) {
      return;
    }
    Infusion infusion = assessment.infusion();
    long now = platform.nowMillis();
    Preview preview = new Preview(center, infusion.id(), assessment.levels(), assessment.have(), now);
    Preview last = previewsByViewer.get(player.uuid());
    if (last != null && last.sameCost(preview) && now - last.shownAt() < PREVIEW_THROTTLE_MILLIS) {
      return;
    }
    previewsByViewer.put(player.uuid(), preview);
    MessageArgument result = trusted("result", platform.resultName(infusion));
    if (assessment.levels() <= 0) {
      platform.tell(player, false, GolemancyMessages.PLINTH_PREVIEW_FREE, result);
    } else if (assessment.verdict() == Verdict.READY) {
      platform.tell(player, false, GolemancyMessages.PLINTH_PREVIEW, result, trusted("levels", assessment.levels()));
    } else {
      platform.tell(player, true, GolemancyMessages.PLINTH_PREVIEW_SHORT, result, trusted("levels", assessment.levels()), trusted("have", assessment.have()));
    }
  }

  private void deny(PlayerRef player, Assessment assessment) {
    switch (assessment.verdict()) {
      case READY -> {
      }
      case NO_RECIPE -> platform.tell(player, true, GolemancyMessages.PLINTH_NO_RECIPE);
      case LOCKED -> platform.tell(player, true, GolemancyMessages.PLINTH_LOCKED);
      case PROTECTED -> platform.tell(player, true, GolemancyMessages.PLINTH_PROTECTED);
      case NOT_ENOUGH_LEVELS -> {
        platform.tell(player, true, GolemancyMessages.PLINTH_LEVELS, trusted("levels", assessment.levels()), trusted("have", assessment.have()));
        platform.denied(player);
      }
    }
  }

  private boolean slotsReady(WorldRef world, PlinthSpot center) {
    for (PlinthSlot slot : PlinthSlot.values()) {
      PlinthSpot spot = center.slot(slot);
      if (!index.isReady(spot.world(), spot.chunkKey()) || !platform.owns(spot.center(world))) {
        return false;
      }
    }
    return true;
  }

  private Map<PlinthSlot, PlinthSlotItem> snapshot(PlinthSpot center) {
    Map<PlinthSlot, PlinthSlotItem> slots = new EnumMap<>(PlinthSlot.class);
    for (PlinthSlot slot : PlinthSlot.values()) {
      index.get(center.slot(slot)).ifPresent(item -> slots.put(slot, item));
    }
    return slots;
  }

  private boolean storedUnchanged(WorldRef world, InfusionRitual ritual) {
    for (PlinthSlot slot : PlinthSlot.values()) {
      if (!Objects.equals(ritual.slots().get(slot), platform.read(world, ritual.center().slot(slot)))) {
        return false;
      }
    }
    return true;
  }

  private void blocksChanging(List<BlockRef> blocks) {
    Set<Id> pedestals = settings.get().pedestals();
    for (BlockRef block : blocks) {
      if (!PlinthStructure.isPart(block.type(), pedestals)) {
        continue;
      }
      PlinthSpot spot = PlinthSpot.of(block);
      abortCovering(spot);
      if (index.get(spot).isEmpty()) {
        continue;
      }
      WorldRef world = block.world();
      platform.runAt(spot.center(world), () -> dropIfDetached(world, spot), REVALIDATE_DELAY_TICKS);
    }
  }

  private void dropIfDetached(WorldRef world, PlinthSpot spot) {
    PlinthSlotItem stored = index.get(spot).orElse(null);
    Id type = platform.blockType(world, spot.x(), spot.y(), spot.z());
    if (stored != null && type != null && !PlinthStructure.isPart(type, settings.get().pedestals())) {
      drop(world, spot, stored);
    }
  }

  private void drop(WorldRef world, PlinthSpot spot, PlinthSlotItem stored) {
    ItemRef item = stored.item();
    if (item == null && !stored.lost()) {
      return;
    }
    if (!release(world, spot, stored) || item == null) {
      return;
    }
    Loc at = spot.center(world);
    saveThen(world, List.of(spot), at, () -> platform.drop(at, item), NOTHING, "the item dropped from the Animus Plinth at " + describe(spot));
  }

  private boolean release(WorldRef world, PlinthSpot spot, PlinthSlotItem expected) {
    boolean taken = take(world, spot, expected);
    if (!taken) {
      index.remove(spot);
      renderer.despawn(spot);
    }
    abortCovering(spot);
    return taken;
  }

  private boolean take(WorldRef world, PlinthSpot spot, PlinthSlotItem expected) {
    if (!expected.equals(platform.read(world, spot))) {
      return false;
    }
    platform.write(world, spot, null);
    index.remove(spot);
    renderer.despawn(spot);
    return true;
  }

  private void saveThen(WorldRef world, List<PlinthSpot> spots, Loc handOutAt, Runnable success, Runnable failure, String subject) {
    Map<Long, PlinthSpot> plates = new LinkedHashMap<>();
    for (PlinthSpot spot : spots) {
      plates.putIfAbsent(GolemRoster.chunkKey(spot.x() >> 9, spot.z() >> 9), spot);
    }
    savePlates(new PlateSave(world, List.copyOf(plates.values()), handOutAt, success, failure, subject), 0, SAVE_ATTEMPTS, 0);
  }

  private void savePlates(PlateSave save, int plate, int attemptsLeft, int delayTicks) {
    platform.async(() -> {
      PlinthSpot spot = save.plates().get(plate);
      boolean saved;
      try {
        saved = platform.savePlate(save.world(), spot.x(), spot.z());
      } catch (RuntimeException failure) {
        AdaptLog.warn("Could not save " + save.subject() + "; it is withheld so it can never be duplicated.", failure);
        handOut(save, save.failure());
        return;
      }
      if (!saved && attemptsLeft > 1) {
        savePlates(save, plate, attemptsLeft - 1, SAVE_RETRY_TICKS);
        return;
      }
      if (!saved) {
        AdaptLog.warn("Could not save " + save.subject() + "; it is withheld so it can never be duplicated.",
            new IllegalStateException("The mantle plate stayed busy for " + SAVE_ATTEMPTS + " save attempts"));
        handOut(save, save.failure());
        return;
      }
      if (plate + 1 < save.plates().size()) {
        savePlates(save, plate + 1, SAVE_ATTEMPTS, 0);
        return;
      }
      handOut(save, save.success());
    }, delayTicks);
  }

  private void handOut(PlateSave save, Runnable task) {
    if (!platform.runAt(save.handOutAt(), task, 0)) {
      AdaptLog.warn("Could not finish " + save.subject() + " because its region refused the task.");
    }
  }

  private void load(WorldRef world, int chunkX, int chunkZ) {
    UUID worldId = world.uid();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    long generation = index.beginLoad(worldId, chunkKey);
    platform.async(() -> {
      Map<PlinthSpot, PlinthSlotItem> stored;
      try {
        stored = platform.readChunk(world, chunkX, chunkZ);
      } catch (RuntimeException failure) {
        AdaptLog.warn("Could not read Animus Plinth items for chunk " + chunkX + ", " + chunkZ + " in " + world.name() + "; they stay hidden until the chunk reloads.", failure);
        stored = Map.of();
      }
      if (stored.isEmpty()) {
        index.completeLoad(worldId, chunkKey, generation, Map.of());
        return;
      }
      Map<PlinthSpot, PlinthSlotItem> slots = stored;
      Loc chunkCenter = new Loc(world, (chunkX << 4) + 8D, world.minHeight(), (chunkZ << 4) + 8D);
      if (!platform.runAt(chunkCenter, () -> finishLoad(world, chunkX, chunkZ, generation, slots), 0)) {
        index.unload(worldId, chunkKey, generation);
      }
    }, 0);
  }

  private void finishLoad(WorldRef world, int chunkX, int chunkZ, long generation, Map<PlinthSpot, PlinthSlotItem> stored) {
    UUID worldId = world.uid();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    if (!index.isCurrent(worldId, chunkKey, generation)) {
      return;
    }
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      index.unload(worldId, chunkKey, generation);
      return;
    }
    Set<Id> pedestals = settings.get().pedestals();
    Map<PlinthSpot, PlinthSlotItem> kept = new HashMap<>(stored.size() * 2);
    Map<PlinthSpot, ItemRef> shown = new HashMap<>(stored.size() * 2);
    Map<PlinthSpot, PlinthSlotItem> detached = new HashMap<>();
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : stored.entrySet()) {
      PlinthSpot spot = entry.getKey();
      PlinthSlotItem slot = entry.getValue();
      ItemRef item = slot.item();
      Id type = platform.blockType(world, spot.x(), spot.y(), spot.z());
      if (slot.lost()) {
        purge(world, spot, slot);
      } else if (item == null) {
        slot.stored().warnUnreadable(() -> "The Animus Plinth item at " + describe(spot) + " in " + world.name()
            + " could not be read. It stays stored on its pedestal, which stays occupied, and is read again the next time it loads.");
        kept.put(spot, slot);
      } else if (type != null && !PlinthStructure.isPart(type, pedestals)) {
        detached.put(spot, slot);
      } else {
        kept.put(spot, slot);
        shown.put(spot, item);
      }
    }
    if (!index.completeLoad(worldId, chunkKey, generation, kept)) {
      return;
    }
    for (Map.Entry<PlinthSpot, ItemRef> entry : shown.entrySet()) {
      renderer.spawn(world, entry.getKey(), entry.getValue());
    }
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : detached.entrySet()) {
      drop(world, entry.getKey(), entry.getValue());
    }
  }

  private void purge(WorldRef world, PlinthSpot spot, PlinthSlotItem item) {
    if (item.equals(platform.read(world, spot))) {
      platform.write(world, spot, null);
      AdaptLog.warn("Removed the Animus Plinth slot at " + describe(spot) + ": an earlier save lost its item and stored nothing for it.");
    }
  }

  private void forget(List<PlinthSpot> spots) {
    for (PlinthSpot spot : spots) {
      renderer.despawn(spot);
    }
  }

  private PlinthStructure.Blocks blocks(WorldRef world) {
    return (x, y, z) -> platform.blockType(world, x, y, z);
  }

  private static boolean holdsCompass(PlayerInventoryRef inventory) {
    return isCompass(inventory.mainHand()) || isCompass(inventory.offHand());
  }

  private static boolean isCompass(ItemRef item) {
    return item != null && COMPASS.equals(item.type());
  }

  private static ItemRef readable(PlinthSlotItem slot) {
    return slot == null ? null : slot.item();
  }

  private static List<ItemRef> pedestalItems(Map<PlinthSlot, PlinthSlotItem> slots) {
    List<ItemRef> items = new ArrayList<>(PlinthSlot.PEDESTALS.size());
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      items.add(readable(slots.get(slot)));
    }
    return items;
  }

  private static String describe(PlinthSpot spot) {
    return spot.x() + ", " + spot.y() + ", " + spot.z();
  }

  private enum Verdict {
    READY,
    NO_RECIPE,
    LOCKED,
    PROTECTED,
    NOT_ENOUGH_LEVELS
  }

  private record Assessment(Verdict verdict, Infusion infusion, int levels, int have) {
    private Assessment withVerdict(Verdict next) {
      return new Assessment(next, infusion, levels, have);
    }
  }

  private record Preview(PlinthSpot center, String infusion, int levels, int have, long shownAt) {
    private boolean sameCost(Preview other) {
      return center.equals(other.center) && infusion.equals(other.infusion) && levels == other.levels && have == other.have;
    }
  }

  private record PlateSave(WorldRef world, List<PlinthSpot> plates, Loc handOutAt, Runnable success, Runnable failure, String subject) {
  }
}
