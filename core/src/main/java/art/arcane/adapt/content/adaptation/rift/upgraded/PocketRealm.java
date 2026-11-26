package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.adaptation.chunk.ChunkLoading;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.rift.RiftAnchors;
import art.arcane.adapt.content.adaptation.rift.RiftBlinkPath;
import art.arcane.adapt.content.adaptation.rift.RiftEnderchest;
import art.arcane.adapt.content.adaptation.rift.RiftGate;
import art.arcane.adapt.content.adaptation.rift.RiftItemMoves;
import art.arcane.adapt.content.adaptation.rift.RiftMagnet;
import art.arcane.adapt.content.adaptation.rift.RiftPearls;
import art.arcane.adapt.content.adaptation.rift.RiftPocket;
import art.arcane.adapt.content.adaptation.rift.RiftRemoteContainers;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Inflated Pocket Dimension, Void Magnet and Easy Enderchest fused, with the anchor network that replaces Rift Gate,
 * Remote Access and Rift Conduit: sneak-punching the air with an empty hand opens your ender chest anywhere, nearby
 * drops are pulled into it, placed blocks are refilled from it, and Rift Anchors carry you, open their containers and
 * send items between them.
 */
public class PocketRealm extends SkillUpgrade<PocketRealm.Config> {
  private static final Id LODESTONE = Id.minecraft("lodestone");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final double ANCHOR_REACH = 8D;
  private static final int WAYSTONE_SEARCH = 16;
  private static final List<Predicate<PlayerRef>> WAYSTONE_KEEPERS = new CopyOnWriteArrayList<>();
  private static final List<BlockFace> SIDES = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
      BlockFace.WEST, BlockFace.EAST);

  private final Cooldowns openCooldowns = cooldowns();
  private final RiftPocket pocket = new RiftPocket(this, (PlayerRef player, Id type) -> true);
  private final RiftMagnet magnet = new RiftMagnet(this, new RiftMagnet.Settings() {
    @Override
    public boolean armed(PlayerRef player) {
      return PocketRealm.this.armed(player);
    }

    @Override
    public int level(PlayerRef player) {
      return getActiveLevel(player, PocketRealm.this::armed);
    }

    @Override
    public double radius(int level) {
      return getPullRadius(level);
    }

    @Override
    public int maxItems(int level) {
      return getPullItems(level);
    }

    @Override
    public int pulseTicks(int level) {
      return Math.max(2, getConfig().pullPulseTicks);
    }

    @Override
    public boolean accepts(PlayerRef player, Id type) {
      return true;
    }

    @Override
    public RiftVoidMagnet.Destination destination(PlayerRef player) {
      return RiftVoidMagnet.Destination.CHEST_FIRST;
    }

    @Override
    public void pulled(PlayerRef player, int moved) {
      addStat(player, "rift.pocket-realm.items-pulled", moved);
      cue(player, MutationCue.WORK);
    }
  });
  private final RiftAnchor anchorTool;
  private final RiftRemoteContainers remote = new RiftRemoteContainers(this,
      (PlayerRef player) -> {
        addStat(player, "rift.pocket-realm.remote-opens", 1);
        cue(player, MutationCue.WORK);
      },
      this::fizzle);
  private final Map<UUID, Trip> trips = new ConcurrentHashMap<>();
  private final Map<UUID, RiftVoidMagnet.Control> controls = playerState();
  private RiftAnchors anchors;

  public PocketRealm() {
    super("upgraded-rift-pocket-realm");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_chest"));
    setInterval(50L);
    anchorTool = registerTool(new RiftAnchor(this));
    registerTool(new RiftEye(this));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, RiftVoidMagnet.CONTROL);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getPullRadius(level), 0), 1);
    statLore(v, getPullItems(level), 2);
    statLore(v, getConfig().refillAmount, 3);
    statLore(v, getAnchors(level), 4);
    statLore(v, Form.duration(getChannelTicks(level) * 50D, 1), 5);
    statLore(v, C.YELLOW, "* ", getConfig().pearlsPerTrip, 6);
    statLore(v, C.YELLOW, "* ", getConfig().stacksPerPearl, 7);
    if (level >= getConfig().crossDimensionLevel) {
      unlockLore(v, RiftMessages.POCKET_REALM_LORE8);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return magnet.hasDemand();
  }

  @Override
  public void onTick() {
    magnet.tick();
  }

  @Override
  public void unregister() {
    magnet.clear();
    trips.clear();
    remote.shutdown();
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking() && control(p) == RiftVoidMagnet.Control.SNEAK) {
      magnet.stop(p.uuid());
      return;
    }
    int level = getActiveLevel(p);
    if (level > 0) {
      magnet.start(p, level);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (control(p) == RiftVoidMagnet.Control.ARMED) {
      int level = getActiveLevel(p);
      if (level > 0) {
        magnet.start(p, level);
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    controls.remove(id);
    magnet.stop(id);
    Trip trip = trips.remove(id);
    if (trip != null) {
      trip.fx().cancel();
    }
    remote.playerLeft(id);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    controls.remove(player.getPlayer().uuid());
  }

  private RiftVoidMagnet.Control control(PlayerRef p) {
    return controls.computeIfAbsent(p.uuid(), (UUID ignored) -> preference(p, RiftVoidMagnet.CONTROL));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    PlayerRef p = e.player();
    int broken = store().takeLost(p.uuid());
    if (broken > 0) {
      p.sendMessage(C.RED + AdaptLanguage.text(RiftMessages.ANCHOR_BROKEN, trusted("count", broken)));
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }
    if (e.action() == InteractAction.RIGHT_CLICK_BLOCK) {
      openFromBlock(e, p);
      return;
    }
    if (e.action() != InteractAction.LEFT_CLICK_AIR || !p.isSneaking()
        || !p.inventory().mainHand().type().isAir() || !hasActiveAdaptation(p)
        || !openCooldowns.isReady(p.uuid(), getConfig().openCooldownMillis)) {
      return;
    }

    openCooldowns.mark(p.uuid(), getConfig().openCooldownMillis);
    RiftEnderchest.open(this, p);
    addStat(p, "rift.pocket-realm.opens", 1);
    cue(p, MutationCue.WORK);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPlaceEvent e) {
    store().forget(e.block());
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    pocket.refillAfterPlace(p, e.block(), getConfig().refillAmount, (PlayerRef player) -> true,
        (PlayerRef player, int moved) -> {
          addStat(player, "rift.pocket-realm.items-refilled", moved);
          cue(player, MutationCue.WORK);
        });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }
    Trip trip = trips.remove(p.uuid());
    if (trip != null) {
      trip.fx().cancel();
      say(p, C.RED, RiftMessages.ANCHOR_CHANNEL_BROKEN);
      fizzle(p);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    BlockRef block = e.block();
    remote.blockChanged(block);
    RiftAnchors.Anchor anchor = anchorAt(block);
    if (anchor == null || !store().remove(anchor)) {
      return;
    }
    e.setDropItems(false);
    e.setExperience(0);
    UUID owner = anchor.owner();
    if (owner.equals(e.player().uuid())) {
      block.world().dropItemNaturally(block.center().position(), anchorTool.create(new MutationTool.NoData()));
      return;
    }
    PlayerRef online = AdaptCore.platform().host().player(owner);
    if (online != null && online.isOnline()) {
      Tasks.runEntity(online, () -> online.sendMessage(C.RED
          + AdaptLanguage.text(RiftMessages.ANCHOR_BROKEN, trusted("count", 1))));
    } else {
      store().markLost(owner);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    e.blocks().removeIf((BlockRef block) -> anchorAt(block) != null);
    for (BlockRef block : e.blocks()) {
      remote.blockChanged(block);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    e.blocks().removeIf((BlockRef block) -> anchorAt(block) != null);
    for (BlockRef block : e.blocks()) {
      remote.blockChanged(block);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityChangeBlockEvent e) {
    if (anchorAt(e.block()) != null) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPistonEvent e) {
    for (BlockRef block : e.blocks()) {
      if (anchorAt(block) != null) {
        e.setCancelled(true);
        return;
      }
    }
    for (BlockRef block : e.blocks()) {
      remote.blockChanged(block);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    remote.viewClosed(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent e) {
    if (e.phase() == ChunkEvent.Phase.UNLOAD) {
      remote.chunkUnloaded(e.world(), e.chunk().x(), e.chunk().z());
    }
  }

  /** Places a Rift Anchor at {@code target}, paid with one {@code item} from the player's hand. */
  void placeAnchor(PlayerRef p, BlockRef target, ItemRef item, int level) {
    int max = getAnchors(level);
    if (store().of(p.uuid()).size() >= max) {
      say(p, C.RED, RiftMessages.ANCHOR_FULL, "max", max);
      return;
    }
    BuildBatch batch = BuildBatch.of(this, p)
        .limit(1)
        .place(target, AdaptCore.platform().host().items().blockState(LODESTONE), item)
        .onDone((BuildResult result) -> placed(p, target, max, result))
        .build();
    BuildBatches.get().start(batch);
  }

  private void placed(PlayerRef p, BlockRef target, int max, BuildResult result) {
    if (result.placed() <= 0) {
      fizzle(p);
      return;
    }
    if (store().add(p.uuid(), target, max) == null) {
      target.setType(Id.minecraft("air"), false);
      for (ItemRef leftover : p.inventory().addItem(anchorTool.create(new MutationTool.NoData()))) {
        p.world().dropItemNaturally(p.location().position(), leftover);
      }
      say(p, C.RED, RiftMessages.ANCHOR_FULL, "max", max);
      return;
    }
    int count = store().of(p.uuid()).size();
    AdaptHud.actionBar(p, C.AQUA + AdaptLanguage.text(RiftMessages.ANCHOR_PLACED, trusted("count", count),
        trusted("max", max)));
    addStat(p, "rift.pocket-realm.anchors-placed", 1);
    cue(p, MutationCue.WORK);
  }

  /** A Rift Eye was used; on one of the player's anchors it opens that anchor's menu, anywhere else travel only. */
  void openFromEye(PlayerRef p, @Nullable BlockRef clicked, int level) {
    RiftAnchors.Anchor here = clicked == null ? null : ownAnchor(p, clicked);
    RiftAnchorMenu.open(this, p, here, store().of(p.uuid()), level);
  }

  private void openFromBlock(PlayerInteractEvent e, PlayerRef p) {
    BlockRef block = e.clickedBlock();
    if (block == null || !block.type().equals(LODESTONE) || p.isSneaking()
        || MutationTools.isTool(p.inventory().mainHand())) {
      return;
    }
    RiftAnchors.Anchor here = ownAnchor(p, block);
    int level = getActiveLevel(p);
    if (here == null || level <= 0) {
      return;
    }
    e.setCancelled(true);
    RiftAnchorMenu.open(this, p, here, store().of(p.uuid()), level);
  }

  private @Nullable RiftAnchors.Anchor anchorAt(BlockRef block) {
    return LODESTONE.equals(block.type()) ? store().at(block) : null;
  }

  /** The player's own anchor at {@code block}, or null. */
  public @Nullable RiftAnchors.Anchor ownAnchor(PlayerRef p, BlockRef block) {
    RiftAnchors.Anchor anchor = store().at(block);
    return anchor != null && anchor.owner().equals(p.uuid()) ? anchor : null;
  }

  /** The player's anchors and waystones, in the order they were made. */
  public List<RiftAnchors.Anchor> anchors(PlayerRef p) {
    return store().of(p.uuid());
  }

  /** How many anchors and waystones the player may have together; 0 without Pocket Realm. */
  public int anchorCap(PlayerRef p) {
    int level = getActiveLevel(p);
    return level <= 0 ? 0 : getAnchors(level);
  }

  /**
   * Sets a waystone for {@code waystone} at {@code feet}, counted against the anchor cap; null when Pocket Realm is not
   * active, the cap is reached or the player already has that waystone.
   */
  public @Nullable RiftAnchors.Anchor addWaystone(PlayerRef p, Loc feet, String waystone, String name) {
    int max = anchorCap(p);
    RiftAnchors.Anchor added = max <= 0 || feet.world() == null ? null : store().addWaystone(p.uuid(),
        feet.world().uid(), new BlockPos(feet.blockX(), feet.blockY(), feet.blockZ()), waystone, name, max);
    if (added == null) {
      say(p, C.RED, RiftMessages.ANCHOR_FULL, "max", max);
      fizzle(p);
      return null;
    }
    AdaptHud.actionBar(p, C.AQUA + AdaptLanguage.text(RiftMessages.ANCHOR_WAYSTONE_SET,
        trusted("count", store().of(p.uuid()).size()), trusted("max", max)));
    cue(p, MutationCue.WORK);
    return added;
  }

  /** The player's waystone for {@code waystone}, or null. */
  public @Nullable RiftAnchors.Anchor waystone(PlayerRef p, String waystone) {
    return store().waystoneOf(p.uuid(), waystone);
  }

  /** Removes one of the player's waystones; anchor blocks are removed by breaking them. */
  public boolean removeWaystone(PlayerRef p, RiftAnchors.Anchor waystone) {
    return waystone.blockless() && waystone.owner().equals(p.uuid()) && store().remove(waystone);
  }

  /**
   * Sends {@code items} into the containers beside the player's anchor {@code target}, at the same price and reach as
   * a send between anchors: one plain ender pearl per started group of stacks, the other dimension only from the
   * cross-dimension level, and only into containers the player may open. {@code rest} gets, on the player's thread,
   * whatever did not arrive, everything when the delivery was refused.
   */
  public void deliver(PlayerRef p, RiftAnchors.Anchor target, List<ItemRef> items, Consumer<List<ItemRef>> rest) {
    int level = getActiveLevel(p);
    Loc targetLoc = target.location();
    int perPearl = Math.max(1, getConfig().stacksPerPearl);
    int pearls = (items.size() + perPearl - 1) / perPearl;
    if (items.isEmpty() || level <= 0 || targetLoc == null || target.blockless()
        || !store().of(p.uuid()).contains(target) || !reaches(p, target, level)
        || RiftPearls.plainPearls(p) < pearls) {
      rest.accept(items);
      return;
    }
    int before = RiftPearls.plainPearls(p);
    if (!RiftPearls.payPlainPearls(this, p, "send", pearls)) {
      rest.accept(items);
      return;
    }
    int paid = Math.max(0, before - RiftPearls.plainPearls(p));
    Loc spill = targetLoc.add(0.5D, 1D, 0.5D);
    ChunkLoading.loadChunkAsync(targetLoc, () -> {
      if (!Tasks.runAt(targetLoc, () -> {
        List<ItemRef> left = items;
        for (BlockRef container : ownContainers(p, targetLoc)) {
          left = RiftItemMoves.put(container.container(), left);
        }
        List<ItemRef> back = left;
        boolean none = RiftItemMoves.total(back) >= RiftItemMoves.total(items);
        returned(p, spill, back, () -> {
          if (none) {
            refund(p, paid);
          } else {
            addStat(p, "rift.pocket-realm.items-sent", RiftItemMoves.total(items) - RiftItemMoves.total(back));
          }
          rest.accept(back);
        });
      })) {
        returned(p, spill, items, () -> {
          refund(p, paid);
          rest.accept(items);
        });
      }
    });
  }

  /** Runs {@code onPlayer} on the player's thread; if they left meanwhile, {@code items} drop at {@code spill}. */
  private static void returned(PlayerRef p, Loc spill, List<ItemRef> items, Runnable onPlayer) {
    Runnable drop = () -> Tasks.runAt(spill, () -> RiftItemMoves.drop(spill, items));
    if (!p.isOnline() || !Tasks.runEntity(p, onPlayer, drop)) {
      drop.run();
    }
  }

  /** Whether the player's level reaches {@code anchor}: always in their own world, elsewhere from the cross-dimension level. */
  boolean reaches(PlayerRef p, RiftAnchors.Anchor anchor, int level) {
    return anchor.worldId().equals(p.world().uid()) || level >= getConfig().crossDimensionLevel;
  }

  /** Starts the channel to {@code target}; it breaks on damage and costs its pearls only when the player leaves. */
  void travel(PlayerRef p, RiftAnchors.Anchor target) {
    int level = getActiveLevel(p);
    if (level <= 0 || !reaches(p, target, level) || trips.containsKey(p.uuid())) {
      return;
    }
    if (target.blockless() && !keepsWaystones(p)) {
      fizzle(p);
      return;
    }
    int pearls = Math.max(0, getConfig().pearlsPerTrip);
    if (RiftPearls.plainPearls(p) < pearls) {
      say(p, C.RED, RiftMessages.ANCHOR_NO_PEARL, "count", pearls);
      fizzle(p);
      return;
    }
    int ticks = getChannelTicks(level);
    FxTimeline fx = RiftGate.channelFx(this, p, ticks);
    Trip trip = new Trip(target, fx);
    trips.put(p.uuid(), trip);
    if (!Tasks.runEntity(p, () -> arriveChecked(p, trip), ticks)) {
      trips.remove(p.uuid(), trip);
      fx.cancel();
    }
  }

  private void arriveChecked(PlayerRef p, Trip trip) {
    if (!trips.remove(p.uuid(), trip) || !p.isOnline() || !hasActiveAdaptation(p)) {
      return;
    }
    RiftAnchors.Anchor target = trip.target();
    Loc anchorLoc = store().of(p.uuid()).contains(target) ? target.location() : null;
    if (anchorLoc == null) {
      blocked(p);
      return;
    }
    ChunkLoading.loadChunkAsync(anchorLoc, () -> {
      if (!Tasks.runAt(anchorLoc, () -> {
        Loc landing = target.blockless() ? waystoneLanding(p, anchorLoc) : anchorLanding(anchorLoc);
        Tasks.runEntity(p, () -> {
          if (landing != null) {
            depart(p, landing);
          } else {
            blocked(p);
          }
        });
      })) {
        Tasks.runEntity(p, () -> blocked(p));
      }
    });
  }

  /** The spot on top of the anchor block, or null when it is gone or covered. */
  private @Nullable Loc anchorLanding(Loc anchorLoc) {
    BlockRef anchor = anchorLoc.world().blockAt(anchorLoc);
    boolean clear = store().at(anchor) != null && anchor.relative(BlockFace.UP).isPassable()
        && anchor.relative(BlockFace.UP, 2).isPassable();
    return clear ? anchorLoc.add(0.5D, 1D, 0.5D) : null;
  }

  /**
   * The nearest safe stand in the waystone's column, searched up then down from where it was recorded, else on the
   * column's highest block, for a structure recorded from the air; null when neither is safe.
   */
  private @Nullable Loc waystoneLanding(PlayerRef p, Loc spot) {
    for (int step = 0; step <= WAYSTONE_SEARCH * 2; step++) {
      int dy = (step + 1) / 2 * (step % 2 == 0 ? -1 : 1);
      Loc feet = spot.add(0.5D, dy, 0.5D);
      if (standable(p, feet)) {
        return feet;
      }
    }
    Loc top = new Loc(spot.world(), spot.blockX() + 0.5D, spot.world().highestBlockY(spot.blockX(), spot.blockZ()) + 1D,
        spot.blockZ() + 0.5D);
    return standable(p, top) ? top : null;
  }

  private static boolean standable(PlayerRef p, Loc feet) {
    return !feet.world().blockAt(feet.add(0D, -1D, 0D)).isPassable() && RiftBlinkPath.isSafeDestination(feet, p.height());
  }

  /** While {@code keeper} holds for a player, they may travel to their waystones; the Waystones signature adds one. */
  public static void addWaystoneKeeper(Predicate<PlayerRef> keeper) {
    WAYSTONE_KEEPERS.add(keeper);
  }

  public static void removeWaystoneKeeper(Predicate<PlayerRef> keeper) {
    WAYSTONE_KEEPERS.remove(keeper);
  }

  private static boolean keepsWaystones(PlayerRef p) {
    for (Predicate<PlayerRef> keeper : WAYSTONE_KEEPERS) {
      if (keeper.test(p)) {
        return true;
      }
    }
    return false;
  }

  private void depart(PlayerRef p, Loc landing) {
    if (!p.isOnline()) {
      return;
    }
    Loc origin = p.location();
    Loc destination = AdaptPorts.get(AdaptationTeleportPort.class, AdaptationTeleportPort.NONE)
        .announce(p, this, origin, landing.withRotation(origin.yaw(), origin.pitch()));
    if (destination == null || !canInteract(p, destination) || !checkRegion(p, destination)) {
      fizzle(p);
      return;
    }
    int pearls = Math.max(0, getConfig().pearlsPerTrip);
    if (pearls > 0 && !RiftPearls.payPlainPearls(this, p, "trip", pearls)) {
      say(p, C.RED, RiftMessages.ANCHOR_NO_PEARL, "count", pearls);
      fizzle(p);
      return;
    }
    CompletableFuture<Boolean> teleport;
    try {
      RiftTeleports.travelling(p, destination);
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error(error);
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((Boolean success, Throwable failure) -> {
      if (failure != null) {
        AdaptLog.error(failure);
      }
      if (Boolean.TRUE.equals(success) && failure == null) {
        Tasks.runEntity(p, () -> {
          RiftGate.arrivalFx(this, origin, p.location());
          addStat(p, "rift.teleports", 1);
          addStat(p, "rift.pocket-realm.anchor-trips", 1);
          cue(p, MutationCue.BOON);
        });
      }
    });
  }

  /** Opens the first container beside {@code target} for a player standing at {@code here}. */
  void openContainer(PlayerRef p, RiftAnchors.Anchor here, RiftAnchors.Anchor target) {
    Loc targetLoc = usable(p, here, target);
    if (targetLoc == null) {
      return;
    }
    ChunkLoading.loadChunkAsync(targetLoc, () -> Tasks.runAt(targetLoc, () -> {
      BlockRef anchor = targetLoc.world().blockAt(targetLoc);
      List<BlockRef> containers = store().at(anchor) != null ? containersBeside(anchor) : List.of();
      Tasks.runEntity(p, () -> {
        if (containers.isEmpty()) {
          say(p, C.RED, RiftMessages.ANCHOR_NO_CONTAINER);
          fizzle(p);
          return;
        }
        remote.open(p, containers.getFirst().location(), this::hasActiveAdaptation);
      });
    }));
  }

  /**
   * Moves whole stacks from the containers beside {@code here} into the containers beside {@code target}, one plain
   * ender pearl per started group of stacks taken; what does not fit goes back, and a send that delivers nothing
   * refunds its pearls.
   */
  void send(PlayerRef p, RiftAnchors.Anchor here, RiftAnchors.Anchor target) {
    Loc hereLoc = here.location();
    Loc targetLoc = usable(p, here, target);
    if (hereLoc == null || targetLoc == null) {
      return;
    }
    int perPearl = Math.max(1, getConfig().stacksPerPearl);
    int wanted = (int) Math.min(Math.max(1, getConfig().sendStacksMax), (long) RiftPearls.plainPearls(p) * perPearl);
    if (wanted <= 0) {
      say(p, C.RED, RiftMessages.ANCHOR_NO_PEARL, "count", 1);
      fizzle(p);
      return;
    }
    Tasks.runAt(hereLoc, () -> {
      List<ItemRef> moving = new ArrayList<>();
      for (BlockRef container : ownContainers(p, hereLoc)) {
        moving.addAll(RiftItemMoves.takeStacks(container.container(), wanted - moving.size()));
      }
      if (moving.isEmpty()) {
        Tasks.runEntity(p, () -> say(p, C.GRAY, RiftMessages.ANCHOR_NOTHING));
        return;
      }
      Runnable putBack = () -> Tasks.runAt(hereLoc, () -> giveBack(p, hereLoc, moving));
      if (!Tasks.runEntity(p, () -> charge(p, hereLoc, targetLoc, moving, perPearl), putBack)) {
        putBack.run();
      }
    });
  }

  private void charge(PlayerRef p, Loc hereLoc, Loc targetLoc, List<ItemRef> moving, int perPearl) {
    int pearls = (moving.size() + perPearl - 1) / perPearl;
    int before = RiftPearls.plainPearls(p);
    if (!RiftPearls.payPlainPearls(this, p, "send", pearls)) {
      say(p, C.RED, RiftMessages.ANCHOR_NO_PEARL, "count", pearls);
      fizzle(p);
      Tasks.runAt(hereLoc, () -> giveBack(p, hereLoc, moving));
      return;
    }
    deliver(p, hereLoc, targetLoc, moving, Math.max(0, before - RiftPearls.plainPearls(p)));
  }

  private void deliver(PlayerRef p, Loc hereLoc, Loc targetLoc, List<ItemRef> moving, int paid) {
    ChunkLoading.loadChunkAsync(targetLoc, () -> {
      boolean scheduled = Tasks.runAt(targetLoc, () -> {
        List<ItemRef> left = moving;
        for (BlockRef container : ownContainers(p, targetLoc)) {
          left = RiftItemMoves.put(container.container(), left);
        }
        int sent = RiftItemMoves.total(moving) - RiftItemMoves.total(left);
        List<ItemRef> back = left;
        Tasks.runAt(hereLoc, () -> giveBack(p, hereLoc, back));
        Tasks.runEntity(p, () -> {
          if (sent <= 0) {
            refund(p, paid);
            say(p, C.GRAY, RiftMessages.ANCHOR_NOTHING);
            return;
          }
          AdaptHud.actionBar(p, C.AQUA + AdaptLanguage.text(RiftMessages.ANCHOR_SENT, trusted("count", sent)));
          addStat(p, "rift.pocket-realm.items-sent", sent);
          cue(p, MutationCue.WORK);
        });
      });
      if (!scheduled) {
        Tasks.runAt(hereLoc, () -> giveBack(p, hereLoc, moving));
        Tasks.runEntity(p, () -> refund(p, paid));
      }
    });
  }

  /** Gives back {@code pearls} plain ender pearls a send took; what does not fit drops at the player. */
  private static void refund(PlayerRef p, int pearls) {
    if (pearls <= 0) {
      return;
    }
    for (ItemRef leftover : p.inventory().addItem(AdaptCore.platform().host().items().create(ENDER_PEARL, pearls))) {
      p.world().dropItemNaturally(p.location().position(), leftover);
    }
  }

  private void giveBack(PlayerRef p, Loc hereLoc, List<ItemRef> items) {
    List<ItemRef> left = items;
    for (BlockRef container : ownContainers(p, hereLoc)) {
      left = RiftItemMoves.put(container.container(), left);
    }
    RiftItemMoves.drop(hereLoc.add(0D, 1D, 0D), left);
  }

  /** The target's corner when the player stands at their own anchor {@code here} and reaches {@code target}. */
  private @Nullable Loc usable(PlayerRef p, RiftAnchors.Anchor here, RiftAnchors.Anchor target) {
    int level = getActiveLevel(p);
    Loc hereLoc = here.location();
    Loc targetLoc = target.location();
    List<RiftAnchors.Anchor> owned = store().of(p.uuid());
    if (level <= 0 || hereLoc == null || targetLoc == null || target.blockless() || !owned.contains(here)
        || !owned.contains(target)
        || !reaches(p, target, level) || !hereLoc.sameWorld(p.location())
        || hereLoc.add(0.5D, 0.5D, 0.5D).distance(p.location()) > ANCHOR_REACH) {
      fizzle(p);
      return null;
    }
    return targetLoc;
  }

  /** The containers beside the anchor at {@code anchorLoc} that the player may open; empty when it is no anchor. */
  private List<BlockRef> ownContainers(PlayerRef p, Loc anchorLoc) {
    BlockRef anchor = anchorLoc.world().blockAt(anchorLoc);
    if (store().at(anchor) == null) {
      return List.of();
    }
    List<BlockRef> usable = new ArrayList<>();
    for (BlockRef container : containersBeside(anchor)) {
      if (remote.canOpen(p, ProtectionProbes.containerBlocks(container, true), container.location())) {
        usable.add(container);
      }
    }
    return usable;
  }

  private List<BlockRef> containersBeside(BlockRef anchor) {
    List<BlockRef> containers = new ArrayList<>();
    for (BlockFace face : SIDES) {
      BlockRef side = anchor.relative(face);
      InventoryRef inventory = side.container();
      if (inventory != null && isStorage(side.state())) {
        containers.add(side);
      }
    }
    return containers;
  }

  private void blocked(PlayerRef p) {
    say(p, C.RED, RiftMessages.ANCHOR_BLOCKED);
    fizzle(p);
  }

  private void fizzle(PlayerRef p) {
    if (p.isOnline()) {
      RiftGate.failureFx(this, p);
    }
  }

  private void say(PlayerRef p, C color, TextKey key) {
    AdaptHud.actionBar(p, color + AdaptLanguage.text(key));
  }

  private void say(PlayerRef p, C color, TextKey key, String placeholder, int number) {
    AdaptHud.actionBar(p, color + AdaptLanguage.text(key, trusted(placeholder, number)));
  }

  private RiftAnchors store() {
    RiftAnchors loaded = anchors;
    if (loaded == null) {
      synchronized (this) {
        if (anchors == null) {
          anchors = RiftAnchors.load();
        }
        loaded = anchors;
      }
    }
    return loaded;
  }

  private boolean armed(PlayerRef player) {
    return player.isSneaking() || preference(player, RiftVoidMagnet.CONTROL) == RiftVoidMagnet.Control.ARMED;
  }

  double getPullRadius(int level) {
    return RiftVoidMagnet.boundedRadius(getConfig().pullRadiusBase + (level * getConfig().pullRadiusPerLevel));
  }

  int getPullItems(int level) {
    return RiftVoidMagnet.boundedItemLimit(getConfig().pullItemsBase + (level * getConfig().pullItemsPerLevel));
  }

  int getAnchors(int level) {
    return Math.max(1, Math.min(16, getConfig().anchorsBase + (level * getConfig().anchorsPerLevel)));
  }

  int getChannelTicks(int level) {
    long millis = getConfig().channelMillisBase - (level * getConfig().channelMillisPerLevel);
    return (int) Math.max(10L, millis / 50L);
  }

  private record Trip(RiftAnchors.Anchor target, FxTimeline fx) {
  }

  @ConfigDescription("Open your ender chest anywhere, pull nearby drops into it, refill placed blocks from it, and travel and send items between Rift Anchors.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Time between two ender chest openings, in milliseconds.", impact = "Lower values allow reopening sooner.")
    long openCooldownMillis = 1000;
    @ConfigDoc(value = "Drop pull radius before level scaling, in blocks.", impact = "Higher values pull drops from farther; capped at 16.")
    double pullRadiusBase = 6;
    @ConfigDoc(value = "Drop pull radius added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double pullRadiusPerLevel = 2;
    @ConfigDoc(value = "Separate drops pulled per pulse before level scaling.", impact = "Higher values clear more drops at once; capped at 32.")
    double pullItemsBase = 12;
    @ConfigDoc(value = "Separate drops pulled per pulse added per level.", impact = "Higher values reward higher levels more.")
    double pullItemsPerLevel = 6;
    @ConfigDoc(value = "Ticks between two pull pulses.", impact = "Lower values pull more often.")
    int pullPulseTicks = 10;
    @ConfigDoc(value = "Most blocks moved into your hand after each placement.", impact = "Higher values refill fuller stacks.")
    int refillAmount = 64;
    @ConfigDoc(value = "Rift Anchors a player may place before level scaling.", impact = "Higher values allow more anchors; capped at 16.")
    int anchorsBase = 1;
    @ConfigDoc(value = "Rift Anchors added per level.", impact = "Higher values reward higher levels more.")
    int anchorsPerLevel = 1;
    @ConfigDoc(value = "Channel before an anchor trip, before level scaling, in milliseconds.", impact = "Higher values make every trip slower.")
    long channelMillisBase = 3500;
    @ConfigDoc(value = "Channel time taken off per level, in milliseconds.", impact = "Higher values make high-level trips faster; never below half a second.")
    long channelMillisPerLevel = 500;
    @ConfigDoc(value = "Level from which anchors in other dimensions can be reached.", impact = "Lower values open other dimensions sooner.")
    int crossDimensionLevel = 3;
    @ConfigDoc(value = "Plain ender pearls spent per anchor trip.", impact = "Higher values make trips cost more.")
    int pearlsPerTrip = 1;
    @ConfigDoc(value = "Stacks sent between anchors per plain ender pearl.", impact = "Higher values make sending cheaper.")
    int stacksPerPearl = 4;
    @ConfigDoc(value = "Most stacks moved by one send.", impact = "Higher values empty bigger storage in one send.")
    int sendStacksMax = 54;
  }
}
