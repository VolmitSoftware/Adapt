package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
  public static final String LOADING_ADAPTATION = InfusionRecipe.RUNIC_AUGMENTS;
  public static final int LOADING_LEVEL = 1;
  private static final int SAVE_ATTEMPTS = 20;
  private static final int SAVE_RETRY_TICKS = 2;
  private static final int REVALIDATE_DELAY_TICKS = 1;
  private static final int PULSE_TICKS = 1;
  private static final int STREAM_INTERVAL_TICKS = 4;
  private static final int HUM_INTERVAL_TICKS = 20;
  private static final float MAX_LIFT = 0.6F;
  private static final float CENTER_LIFT_SHARE = 0.5F;
  private static final float MAX_CENTER_DEGREES_PER_TICK = 30F;
  private static final double LIFT_PHASE = 3D;
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

  PlinthService(PlinthPlatform platform, Supplier<PlinthSettings> settings, Supplier<PlinthHost> host) {
    super("golemancy", "golem-plinths");
    this.platform = Objects.requireNonNull(platform);
    this.settings = Objects.requireNonNull(settings);
    this.host = Objects.requireNonNull(host);
    this.renderer = new PlinthRenderer(platform);
  }

  public static PlinthService create(Supplier<PlinthSettings> settings, Supplier<PlinthHost> host) {
    return new PlinthService(new BukkitPlinthPlatform(host), settings, host);
  }

  public void indexLoadedChunks() {
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        load(world, chunk.getX(), chunk.getZ());
      }
    }
  }

  public Optional<PlinthSlotItem> slot(PlinthSpot spot) {
    return index.get(spot);
  }

  public Optional<InfusionRitual> ritual(PlinthSpot center) {
    return Optional.ofNullable(ritualsByCenter.get(center));
  }

  public boolean isPlinthBlock(Block block) {
    Set<Material> pedestals = settings.get().pedestals();
    return PlinthStructure.isPart(block.getType(), pedestals)
        && PlinthStructure.locate(blocks(block.getWorld()), PlinthSpot.of(block), pedestals).isPresent();
  }

  public void shutdown() {
    unregister();
    for (InfusionRitual ritual : List.copyOf(ritualsByCenter.values())) {
      if (ritual.finish()) {
        forget(ritual);
      }
    }
    renderer.shutdown();
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Block block = e.getClickedBlock();
    if (ProtectionEventProbe.isActive(e) || e.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
      return;
    }
    PlinthSettings current = settings.get();
    Material type = block.getType();
    PlinthHost activeHost = host.get();
    if (!PlinthStructure.isPart(type, current.pedestals()) || activeHost == null) {
      return;
    }
    Player player = e.getPlayer();
    PlayerInventory inventory = player.getInventory();
    ItemStack held = inventory.getItemInMainHand();
    boolean lodestone = type == Material.LODESTONE;
    World world = block.getWorld();
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
    if (e.getHand() == EquipmentSlot.HAND) {
      load(player, world, target.get().spot(), held, activeHost);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (ProtectionEventProbe.isActive(e)) {
      return;
    }
    blocksChanging(List.of(e.getBlock()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockExplodeEvent e) {
    blocksChanging(e.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityExplodeEvent e) {
    blocksChanging(e.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent e) {
    blocksChanging(e.getBlocks());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent e) {
    blocksChanging(e.getBlocks());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityChangeBlockEvent e) {
    if (!PlinthStructure.isPart(e.getTo(), settings.get().pedestals())) {
      blocksChanging(List.of(e.getBlock()));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    InfusionRitual ritual = ritualsByPlayer.get(e.getPlayer().getUniqueId());
    if (ritual != null) {
      onPlinthThread(ritual, () -> abort(ritual, false));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent e) {
    Chunk chunk = e.getChunk();
    if (e.isNewChunk()) {
      UUID worldId = chunk.getWorld().getUID();
      long chunkKey = GolemRoster.chunkKey(chunk.getX(), chunk.getZ());
      index.completeLoad(worldId, chunkKey, index.beginLoad(worldId, chunkKey), Map.of());
      return;
    }
    load(chunk.getWorld(), chunk.getX(), chunk.getZ());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkUnloadEvent e) {
    Chunk chunk = e.getChunk();
    forget(index.unload(chunk.getWorld().getUID(), GolemRoster.chunkKey(chunk.getX(), chunk.getZ())));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldUnloadEvent e) {
    forget(index.unloadWorld(e.getWorld().getUID()));
  }

  private void crookClicked(PlayerInteractEvent e, Player player, PlayerInventory inventory, World world, PlinthSpot center, PlinthHost activeHost,
                            PlinthSettings current) {
    boolean offHand = e.getHand() != EquipmentSlot.HAND;
    if ((offHand && isCompass(inventory.getItemInOffHand()))
        || !PlinthStructure.valid(blocks(world), center, current.pedestals())
        || activeHost.level(player, LOADING_ADAPTATION) < LOADING_LEVEL) {
      return;
    }
    e.setCancelled(true);
    if (e.getHand() == EquipmentSlot.HAND) {
      begin(player, world, center, activeHost, current);
    }
  }

  private void load(Player player, World world, PlinthSpot spot, ItemStack held, PlinthHost activeHost) {
    Location at = spot.center(world);
    if (!index.isReady(spot.world(), spot.chunkKey()) || !platform.owns(at)) {
      platform.tell(player, true, GolemancyMessages.PLINTH_LOADING);
      return;
    }
    if (!activeHost.canStore(player, LOADING_ADAPTATION, at)) {
      platform.tell(player, true, GolemancyMessages.PLINTH_PROTECTED);
      return;
    }
    PlinthSlotItem current = platform.read(world, spot);
    if (held == null || held.getType() == Material.AIR) {
      if (current != null) {
        retrieve(player, world, spot, current);
      }
      return;
    }
    if (current != null) {
      platform.tell(player, true, GolemancyMessages.PLINTH_OCCUPIED);
      return;
    }
    ItemStack single = platform.takeOne(player);
    PlinthSlotItem stored = PlinthSlotItem.of(UUID.randomUUID(), single);
    platform.write(world, spot, stored);
    platform.saveData(player);
    index.put(spot, stored);
    renderer.spawn(world, spot, single);
    abortCovering(spot);
    platform.effect(PlinthEffect.LOADED, at, 0D);
  }

  private void retrieve(Player player, World world, PlinthSpot spot, PlinthSlotItem stored) {
    if (!release(world, spot, stored)) {
      return;
    }
    Location at = spot.center(world);
    platform.effect(PlinthEffect.RETRIEVED, at, 0D);
    if (!stored.readable()) {
      return;
    }
    saveThen(world, List.of(spot), at, () -> handBack(player, at, stored.item()), NOTHING, "the item taken from the Animus Plinth at " + describe(spot));
  }

  private void handBack(Player player, Location at, ItemStack item) {
    if (player.isOnline() && platform.owns(player)) {
      platform.give(player, item);
    } else {
      platform.drop(at, item);
    }
  }

  private void begin(Player player, World world, PlinthSpot center, PlinthHost activeHost, PlinthSettings current) {
    long now = platform.nowMillis();
    if (!slotsReady(world, center)) {
      platform.tell(player, true, GolemancyMessages.PLINTH_LOADING);
      return;
    }
    if (busy(ritualsByCenter.get(center), now) || busy(ritualsByPlayer.get(player.getUniqueId()), now)) {
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
    ritualsByPlayer.put(player.getUniqueId(), ritual);
    platform.effect(PlinthEffect.STARTED, center.center(world), 0D);
    schedulePulse(world, ritual);
  }

  private void schedulePulse(World world, InfusionRitual ritual) {
    if (!platform.runAt(ritual.center().center(world), () -> pulse(world, ritual), PULSE_TICKS)) {
      abort(ritual, true);
    }
  }

  private void pulse(World world, InfusionRitual ritual) {
    if (ritual.isFinished()) {
      return;
    }
    ritual.pulsed(platform.nowMillis());
    Player player = ritual.player();
    PlinthSpot center = ritual.center();
    boolean online = player.isOnline();
    boolean present = online && player.isValid() && !player.isDead() && platform.owns(player);
    Location playerAt = present ? player.getLocation() : null;
    boolean sameWorld = playerAt != null && world.equals(playerAt.getWorld());
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

  private void animate(World world, InfusionRitual ritual) {
    PlinthSpot center = ritual.center();
    double progress = ritual.progress();
    float lift = (float) (MAX_LIFT * Math.min(1D, progress * LIFT_PHASE));
    float centerLift = lift * CENTER_LIFT_SHARE;
    float centerSpin = PlinthRenderer.IDLE_DEGREES_PER_TICK + (float) ((MAX_CENTER_DEGREES_PER_TICK - PlinthRenderer.IDLE_DEGREES_PER_TICK) * progress);
    renderer.animate(center, centerLift, centerSpin);
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      renderer.animate(center.slot(slot), lift, PlinthRenderer.IDLE_DEGREES_PER_TICK);
    }
    if (ritual.elapsed() % STREAM_INTERVAL_TICKS == 0) {
      Location target = PlinthRenderer.itemAt(world, center, centerLift);
      for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
        platform.stream(PlinthRenderer.itemAt(world, center.slot(slot), lift), target);
      }
    }
    if (ritual.elapsed() % HUM_INTERVAL_TICKS == 0) {
      platform.effect(PlinthEffect.HUM, center.center(world), progress);
    }
  }

  private void complete(World world, InfusionRitual ritual) {
    if (!ritual.finish()) {
      return;
    }
    forget(ritual);
    Player player = ritual.player();
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
        Adapt.warn("The Animus Plinth item at " + describe(spot) + " changed while its infusion completed; the infusion was cancelled and its items restored.");
        restore(world, removed);
        resync(world, spot);
        refund(player, charge.get(), false);
        interrupted(world, ritual, true);
        return;
      }
      removed.put(spot, entry.getValue());
    }
    Location at = center.center(world);
    saveThen(world, List.copyOf(removed.keySet()), at, () -> deliver(world, player, center, infusion, charge.get()), () -> {
      restore(world, removed);
      refund(player, charge.get(), true);
    }, "the infusion at the Animus Plinth at " + describe(center));
  }

  private void deliver(World world, Player player, PlinthSpot center, Infusion infusion, PlinthCharge charge) {
    Location at = new Location(world, center.x() + CENTER, center.y() + RESULT_HEIGHT, center.z() + CENTER);
    platform.popResult(at, infusion.resultItem().get());
    charge.settle();
    platform.effect(PlinthEffect.COMPLETED, at, 1D);
    platform.tell(player, false, GolemancyMessages.PLINTH_COMPLETE);
    PlinthHost activeHost = host.get();
    if (activeHost != null) {
      activeHost.infused(player, infusion, at);
    }
  }

  private void refund(Player player, PlinthCharge charge, boolean announce) {
    if (platform.owns(player)) {
      charge.refund();
    } else {
      platform.runEntity(player, charge::refund, 0);
    }
    if (announce) {
      platform.tell(player, true, GolemancyMessages.PLINTH_FAILED);
    }
  }

  private void restore(World world, Map<PlinthSpot, PlinthSlotItem> removed) {
    Set<Material> pedestals = settings.get().pedestals();
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : removed.entrySet()) {
      PlinthSpot spot = entry.getKey();
      PlinthSlotItem item = entry.getValue();
      Material type = platform.blockType(world, spot.x(), spot.y(), spot.z());
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

  private void resync(World world, PlinthSpot spot) {
    PlinthSlotItem stored = platform.read(world, spot);
    if (stored == null || !stored.readable()) {
      index.remove(spot);
      renderer.despawn(spot);
      return;
    }
    if (index.put(spot, stored)) {
      renderer.spawn(world, spot, stored.item());
    }
  }

  private void abort(InfusionRitual ritual, boolean announce) {
    if (!ritual.finish()) {
      return;
    }
    forget(ritual);
    World world = platform.world(ritual.center().world());
    if (world != null) {
      interrupted(world, ritual, false);
    }
    if (announce) {
      platform.tell(ritual.player(), true, GolemancyMessages.PLINTH_INTERRUPTED);
    }
  }

  private void interrupted(World world, InfusionRitual ritual, boolean announce) {
    PlinthSpot center = ritual.center();
    for (PlinthSlot slot : PlinthSlot.values()) {
      renderer.rest(center.slot(slot));
    }
    platform.effect(PlinthEffect.FIZZLE, center.center(world), ritual.progress());
    if (announce) {
      platform.tell(ritual.player(), true, GolemancyMessages.PLINTH_INTERRUPTED);
    }
  }

  private void forget(InfusionRitual ritual) {
    ritualsByCenter.remove(ritual.center(), ritual);
    ritualsByPlayer.remove(ritual.player().getUniqueId(), ritual);
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
    World world = platform.world(ritual.center().world());
    if (world == null) {
      lose(ritual);
      return;
    }
    Location at = ritual.center().center(world);
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

  private Assessment assess(Player player, World world, PlinthSpot center, Map<PlinthSlot, PlinthSlotItem> slots, PlinthHost activeHost, PlinthSettings current) {
    Optional<Infusion> match = current.recipes().match(readable(slots.get(PlinthSlot.CENTER)), pedestalItems(slots));
    if (match.isEmpty()) {
      return new Assessment(Verdict.NO_RECIPE, null, 0);
    }
    Infusion infusion = match.get();
    int levels = InfusionRitual.levelCost(infusion, player.getGameMode() == GameMode.CREATIVE);
    if (activeHost.level(player, infusion.unlockAdaptation()) < infusion.unlockLevel()) {
      return new Assessment(Verdict.LOCKED, infusion, levels);
    }
    if (!activeHost.canInfuse(player, infusion.unlockAdaptation(), center.center(world))) {
      return new Assessment(Verdict.PROTECTED, infusion, levels);
    }
    if (player.getLevel() < levels) {
      return new Assessment(Verdict.NOT_ENOUGH_LEVELS, infusion, levels);
    }
    return new Assessment(Verdict.READY, infusion, levels);
  }

  private void deny(Player player, Assessment assessment) {
    switch (assessment.verdict()) {
      case READY -> {
      }
      case NO_RECIPE -> platform.tell(player, true, GolemancyMessages.PLINTH_NO_RECIPE);
      case LOCKED -> platform.tell(player, true, GolemancyMessages.PLINTH_LOCKED);
      case PROTECTED -> platform.tell(player, true, GolemancyMessages.PLINTH_PROTECTED);
      case NOT_ENOUGH_LEVELS -> platform.tell(player, true, GolemancyMessages.PLINTH_LEVELS, trusted("levels", assessment.levels()));
    }
  }

  private boolean slotsReady(World world, PlinthSpot center) {
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

  private boolean storedUnchanged(World world, InfusionRitual ritual) {
    for (PlinthSlot slot : PlinthSlot.values()) {
      if (!Objects.equals(ritual.slots().get(slot), platform.read(world, ritual.center().slot(slot)))) {
        return false;
      }
    }
    return true;
  }

  private void blocksChanging(List<Block> blocks) {
    Set<Material> pedestals = settings.get().pedestals();
    for (Block block : blocks) {
      if (!PlinthStructure.isPart(block.getType(), pedestals)) {
        continue;
      }
      PlinthSpot spot = PlinthSpot.of(block);
      abortCovering(spot);
      if (index.get(spot).isEmpty()) {
        continue;
      }
      World world = block.getWorld();
      platform.runAt(spot.center(world), () -> dropIfDetached(world, spot), REVALIDATE_DELAY_TICKS);
    }
  }

  private void dropIfDetached(World world, PlinthSpot spot) {
    PlinthSlotItem stored = index.get(spot).orElse(null);
    Material type = platform.blockType(world, spot.x(), spot.y(), spot.z());
    if (stored != null && type != null && !PlinthStructure.isPart(type, settings.get().pedestals())) {
      drop(world, spot, stored);
    }
  }

  private void drop(World world, PlinthSpot spot, PlinthSlotItem stored) {
    if (!release(world, spot, stored) || !stored.readable()) {
      return;
    }
    Location at = spot.center(world);
    saveThen(world, List.of(spot), at, () -> platform.drop(at, stored.item()), NOTHING, "the item dropped from the Animus Plinth at " + describe(spot));
  }

  private boolean release(World world, PlinthSpot spot, PlinthSlotItem expected) {
    boolean taken = take(world, spot, expected);
    if (!taken) {
      index.remove(spot);
      renderer.despawn(spot);
    }
    abortCovering(spot);
    return taken;
  }

  private boolean take(World world, PlinthSpot spot, PlinthSlotItem expected) {
    if (!expected.equals(platform.read(world, spot))) {
      return false;
    }
    platform.write(world, spot, null);
    index.remove(spot);
    renderer.despawn(spot);
    return true;
  }

  private void saveThen(World world, List<PlinthSpot> spots, Location handOutAt, Runnable success, Runnable failure, String subject) {
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
        Adapt.warn("Could not save " + save.subject() + "; it is withheld so it can never be duplicated.", failure);
        handOut(save, save.failure());
        return;
      }
      if (!saved && attemptsLeft > 1) {
        savePlates(save, plate, attemptsLeft - 1, SAVE_RETRY_TICKS);
        return;
      }
      if (!saved) {
        Adapt.warn("Could not save " + save.subject() + "; it is withheld so it can never be duplicated.",
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
      Adapt.warn("Could not finish " + save.subject() + " because its region refused the task.");
    }
  }

  private void load(World world, int chunkX, int chunkZ) {
    UUID worldId = world.getUID();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    long generation = index.beginLoad(worldId, chunkKey);
    platform.async(() -> {
      Map<PlinthSpot, PlinthSlotItem> stored;
      try {
        stored = platform.readChunk(world, chunkX, chunkZ);
      } catch (RuntimeException failure) {
        Adapt.warn("Could not read Animus Plinth items for chunk " + chunkX + ", " + chunkZ + " in " + world.getName() + "; they stay hidden until the chunk reloads.", failure);
        stored = Map.of();
      }
      if (stored.isEmpty()) {
        index.completeLoad(worldId, chunkKey, generation, Map.of());
        return;
      }
      Map<PlinthSpot, PlinthSlotItem> slots = stored;
      Location chunkCenter = new Location(world, (chunkX << 4) + 8D, world.getMinHeight(), (chunkZ << 4) + 8D);
      if (!platform.runAt(chunkCenter, () -> finishLoad(world, chunkX, chunkZ, generation, slots), 0)) {
        index.unload(worldId, chunkKey, generation);
      }
    }, 0);
  }

  private void finishLoad(World world, int chunkX, int chunkZ, long generation, Map<PlinthSpot, PlinthSlotItem> stored) {
    UUID worldId = world.getUID();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    if (!index.isCurrent(worldId, chunkKey, generation)) {
      return;
    }
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      index.unload(worldId, chunkKey, generation);
      return;
    }
    Set<Material> pedestals = settings.get().pedestals();
    Map<PlinthSpot, PlinthSlotItem> kept = new HashMap<>(stored.size() * 2);
    Map<PlinthSpot, PlinthSlotItem> detached = new HashMap<>();
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : stored.entrySet()) {
      PlinthSpot spot = entry.getKey();
      PlinthSlotItem item = entry.getValue();
      Material type = platform.blockType(world, spot.x(), spot.y(), spot.z());
      if (!item.readable()) {
        purge(world, spot, item);
      } else if (type != null && !PlinthStructure.isPart(type, pedestals)) {
        detached.put(spot, item);
      } else {
        kept.put(spot, item);
      }
    }
    if (!index.completeLoad(worldId, chunkKey, generation, kept)) {
      return;
    }
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : kept.entrySet()) {
      renderer.spawn(world, entry.getKey(), entry.getValue().item());
    }
    for (Map.Entry<PlinthSpot, PlinthSlotItem> entry : detached.entrySet()) {
      drop(world, entry.getKey(), entry.getValue());
    }
  }

  private void purge(World world, PlinthSpot spot, PlinthSlotItem item) {
    if (item.equals(platform.read(world, spot))) {
      platform.write(world, spot, null);
      Adapt.warn("Removed the unreadable Animus Plinth item at " + describe(spot) + ".");
    }
  }

  private void forget(List<PlinthSpot> spots) {
    for (PlinthSpot spot : spots) {
      renderer.despawn(spot);
    }
  }

  private PlinthStructure.Blocks blocks(World world) {
    return (x, y, z) -> platform.blockType(world, x, y, z);
  }

  private static boolean holdsCompass(PlayerInventory inventory) {
    return isCompass(inventory.getItemInMainHand()) || isCompass(inventory.getItemInOffHand());
  }

  private static boolean isCompass(ItemStack item) {
    return item != null && item.getType() == Material.COMPASS;
  }

  private static ItemStack readable(PlinthSlotItem slot) {
    return slot == null ? null : slot.item();
  }

  private static List<ItemStack> pedestalItems(Map<PlinthSlot, PlinthSlotItem> slots) {
    List<ItemStack> items = new ArrayList<>(PlinthSlot.PEDESTALS.size());
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

  private record Assessment(Verdict verdict, Infusion infusion, int levels) {
    private Assessment withVerdict(Verdict next) {
      return new Assessment(next, infusion, levels);
    }
  }

  private record PlateSave(World world, List<PlinthSpot> plates, Location handOutAt, Runnable success, Runnable failure, String subject) {
  }
}
