package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.mutation.MutationEventClaims;
import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.potion.AdaptBrewCompleteEvent;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class MutationRuntimeRouter implements Listener {
  private static final int RELOAD_CLEANUP_BATCH = 16;

  private final MutationRuntimeAccess access = new MutationRuntimeAccess();
  private final MutationRuntimeStore store = new MutationRuntimeStore();
  private final MutationItemIdentity items = new MutationItemIdentity(Adapt.instance);
  private final MutationBlockProvenance provenance = new MutationBlockProvenance();
  private final MutationEntityResolver resolver = new MutationEntityResolver();
  private final MutationEquipmentRuntime equipment = new MutationEquipmentRuntime(access, store, items);
  private final MutationMovementRuntime movement = new MutationMovementRuntime(access, store, resolver);
  private final MutationEffectRuntime effects = new MutationEffectRuntime(access, store, provenance);
  private final MutationWorldRuntime world = new MutationWorldRuntime(access, store, provenance, equipment);
  private final MutationCombatRuntime combat = new MutationCombatRuntime(access, store, resolver, provenance, world);
  private final MutationFormulaRuntime formula = new MutationFormulaRuntime(access, store, combat);
  private final AtomicLong reloadGeneration = new AtomicLong();

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onMovementControl(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    if (eligible(player) && access.anyExpressed(player)) {
      movement.enforceBastionMovement(event);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    if (!eligible(player)) {
      return;
    }
    boolean expressing = access.anyExpressed(player);
    if (expressing && event.getTo() != null) {
      equipment.onDepthTransition(player, event.getFrom().getBlockY(), event.getTo().getBlockY());
    }
    if (movedPosition(event)) {
      effects.interruptMolt(player);
    }
    if (expressing) {
      movement.onMove(event);
      combat.onMove(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent event) {
    if (eligible(event.getPlayer())) {
      effects.onToggleSneak(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent event) {
    if (eligible(event.getPlayer())) {
      movement.onToggleSprint(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerVelocityEvent event) {
    if (eligible(event.getPlayer())) {
      movement.onVelocity(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent event) {
    if (ProtectionEventProbe.isActive(event)) {
      return;
    }

    if (!eligible(event.getPlayer())) {
      return;
    }
    MutationEventClaims claims = new MutationEventClaims();
    movement.onInteract(event);
    world.onInteract(event, claims);
    combat.onInteract(event);
    if (equipment.blocksItem(event.getItem())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent event) {
    if (event.getEntity().getShooter() instanceof Player player && eligible(player)) {
      movement.onProjectileLaunch(event);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileHitEvent event) {
    movement.onProjectileHit(event);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent event) {
    if (eligible(event.getPlayer())) {
      movement.onTeleport(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerSwapHandItemsEvent event) {
    if (eligible(event.getPlayer())) {
      movement.onSwapHands(event);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onHeldItemDamagePreflight(EntityDamageByEntityEvent event) {
    if (!access.enabled()) {
      return;
    }
    Player dealer = resolver.playerSource(event.getDamager());
    Player receiver = event.getEntity() instanceof Player player ? player : null;
    if (dealer == null || !eligible(dealer) || (receiver != null && !eligible(receiver))) {
      return;
    }
    if (equipment.blocksHeldItem(dealer)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent event) {
    if (!access.enabled()) {
      return;
    }
    Player dealer = resolver.playerSource(event.getDamager());
    Player receiver = event.getEntity() instanceof Player player ? player : null;
    if ((dealer != null && !eligible(dealer)) || (receiver != null && !eligible(receiver))) {
      return;
    }
    MutationEventClaims claims = new MutationEventClaims();
    movement.onDamage(event, claims);
    combat.onDamage(event, claims);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (!access.enabled()) {
      return;
    }
    if (event.getEntity() instanceof Player player && !eligible(player)) {
      return;
    }
    equipment.onIncomingDamage(event);
    effects.onDamage(event);
    if (event.getEntity() instanceof Player player && isEnvironmentalFire(event.getCause())) {
      world.clearEnvironmentalCharge(player);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityPotionEffectEvent event) {
    effects.onPotionEffect(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerItemDamageEvent event) {
    if (eligible(event.getPlayer())) {
      equipment.onItemDamage(event, new MutationEventClaims());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerItemMendEvent event) {
    if (recoveryEligible(event.getPlayer())) {
      equipment.onItemMend(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    if (ProtectionEventProbe.isActive(event)) {
      return;
    }
    world.onBlockBreak(event);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSuccessful(BlockBreakEvent event) {
    if (ProtectionEventProbe.isActive(event)) {
      return;
    }
    if (eligible(event.getPlayer())) {
      world.onSuccessfulBlockBreak(event);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    if (ProtectionEventProbe.isActive(event)) {
      return;
    }
    if (eligible(event.getPlayer())) {
      world.onBlockPlace(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBurnEvent event) {
    world.onBurn(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockIgniteEvent event) {
    world.onIgnite(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockExplodeEvent event) {
    world.onBlockExplosion(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityExplodeEvent event) {
    world.onEntityExplosion(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent event) {
    world.onPistonExtend(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent event) {
    world.onPistonRetract(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockFromToEvent event) {
    world.onFluid(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockFadeEvent event) {
    world.onFade(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityChangeBlockEvent event) {
    world.onEntityChangeBlock(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockDropItemEvent event) {
    world.onDrop(event);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent event) {
    world.onChunkLoad(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityRegainHealthEvent event) {
    if (event.getEntity() instanceof Player player && eligible(player)) {
      world.onRegainHealth(event);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CreatureSpawnEvent event) {
    if (access.enabled()) {
      combat.onSpawn(event);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent event) {
    combat.onDeath(event);
    if (event.getEntity() instanceof Player player) {
      if (access.enabled()) {
        formula.onDeath(player);
      }
      cleanupPlayer(player, true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityTargetLivingEntityEvent event) {
    if (!access.enabled()) {
      return;
    }
    if (event.getTarget() instanceof Player player && !eligible(player)) {
      return;
    }
    combat.onTarget(event);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CraftItemEvent event) {
    if (!(event.getWhoClicked() instanceof Player player) || !eligible(player)) {
      return;
    }
    equipment.onCraft(event);
    formula.onCraft(event);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent event) {
    if (access.enabled()) {
      formula.onBrew(event);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EnchantItemEvent event) {
    if (eligible(event.getEnchanter())) {
      formula.onEnchant(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareAnvilEvent event) {
    Player player = event.getView().getPlayer() instanceof Player viewer ? viewer : null;
    if (recoveryEligible(player)) {
      equipment.onPrepareAnvil(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareGrindstoneEvent event) {
    Player player = event.getView().getPlayer() instanceof Player viewer ? viewer : null;
    if (recoveryEligible(player)) {
      equipment.onPrepareGrindstone(event);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareSmithingEvent event) {
    Player player = event.getView().getPlayer() instanceof Player viewer ? viewer : null;
    if (recoveryEligible(player)) {
      equipment.onPrepareSmithing(event);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(InventoryClickEvent event) {
    Player player = event.getWhoClicked() instanceof Player clicked ? clicked : null;
    if (eligible(player)) {
      scheduleEquipmentValidation(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(InventoryDragEvent event) {
    Player player = event.getWhoClicked() instanceof Player clicked ? clicked : null;
    if (eligible(player)) {
      scheduleEquipmentValidation(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent event) {
    cleanupPlayer(event.getPlayer(), true);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    cleanupPlayer(event.getPlayer(), true);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent event) {
    store.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent event) {
    store.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerGameModeChangeEvent event) {
    if (!MutationWorldRuntime.eligibleGameMode(event.getNewGameMode())) {
      cleanupPlayer(event.getPlayer(), true);
    } else {
      store.remove(event.getPlayer().getUniqueId());
    }
  }

  public boolean attuneCurrentArmor(Player player) {
    return eligible(player) && equipment.attuneCurrentArmor(player);
  }

  public boolean dissolveTemperbound(Player player) {
    return eligible(player) && equipment.dissolveTemperbound(player);
  }

  public boolean bindMasterwork(Player player) {
    return eligible(player) && equipment.bindMasterwork(player);
  }

  public boolean abandonMasterwork(Player player) {
    return eligible(player) && equipment.abandonMasterwork(player);
  }

  public boolean bindDeepbloodTool(Player player) {
    return eligible(player) && equipment.bindDeepbloodTool(player);
  }

  public boolean emitAnomalyUtility(
      Player actor,
      LivingEntity target,
      MutationUtilityTag tag,
      double strength,
      boolean recursiveEcho
  ) {
    if (recursiveEcho || !eligible(actor)) {
      return false;
    }
    return formula.echoUtility(actor, target, tag, strength, new MutationEventClaims());
  }

  public void onLoadoutChanged(Player player, MutationSnapshot previous, MutationSnapshot current) {
    if (player == null || equivalent(previous, current)) {
      return;
    }
    cleanupPlayer(player, false);
  }

  public void onConfigReload() {
    long generation = reloadGeneration.incrementAndGet();
    if (!access.enabled()) {
      combat.clearGlobalState();
    }
    List<Player> online = new ArrayList<>(Adapt.instance.getAdaptServer().getOnlinePlayerSnapshot());
    scheduleReloadCleanup(online, 0, generation);
  }

  public void recoverLoadedState() {
    world.recoverLoadedState();
  }

  public void shutdown() {
    List<Player> online = new ArrayList<>(Adapt.instance.getAdaptServer().getOnlinePlayerSnapshot());
    cleanupTrackedMarkers();
    if (J.isFoliaThreading()) {
      int rejected = 0;
      for (Player player : online) {
        if (!J.runEntity(player, () -> cleanupPlayer(player, true))) {
          rejected++;
        }
      }
      if (rejected > 0) {
        Adapt.warn("Mutation shutdown deferred cleanup for " + rejected
            + " player(s); persistent world markers will be recovered on the next enable.");
      }
      world.shutdown();
      return;
    }
    for (Player player : online) {
      cleanupPlayer(player, true);
    }
    world.shutdown();
    store.clear();
  }

  private void cleanupPlayer(Player player, boolean removeState) {
    if (player == null) {
      return;
    }
    effects.reconcileRecipient(player);
    effects.cleanup(player);
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      runtime.loadoutGeneration = store.nextLoadoutGeneration();
    }
    movement.cleanup(player);
    combat.cleanup(player);
    world.cleanup(player);
    formula.cleanup(player);
    synchronized (runtime) {
      runtime.clearTransient();
    }
    if (removeState) {
      store.remove(player.getUniqueId());
    }
  }

  private void scheduleEquipmentValidation(Player player) {
    if (player != null) {
      J.runEntity(player, () -> equipment.validateTemperbound(player), 1);
    }
  }

  private void cleanupTrackedMarkers() {
    if (J.isFoliaThreading()) {
      for (MutationRuntimeStore.PlayerRuntimeState runtime : new ArrayList<>(store.players.values())) {
        Entity marker;
        synchronized (runtime) {
          marker = runtime.paradox.marker;
        }
        if (marker != null) {
          J.runEntity(marker, marker::remove);
        }
      }
      store.paradoxMarkers.clear();
      return;
    }
    for (UUID markerId : new ArrayList<>(store.paradoxMarkers.keySet())) {
      Entity marker = Bukkit.getEntity(markerId);
      if (marker == null) {
        store.paradoxMarkers.remove(markerId);
        continue;
      }
      marker.remove();
      store.paradoxMarkers.remove(markerId);
    }
  }

  private void scheduleReloadCleanup(List<Player> players, int start, long generation) {
    if (generation != reloadGeneration.get() || start >= players.size()) {
      return;
    }
    int end = Math.min(players.size(), start + RELOAD_CLEANUP_BATCH);
    for (int index = start; index < end; index++) {
      Player player = players.get(index);
      J.runEntity(player, () -> {
        if (generation == reloadGeneration.get()) {
          cleanupPlayer(player, false);
        }
      });
    }
    if (end < players.size()) {
      J.s(() -> scheduleReloadCleanup(players, end, generation), 1);
    }
  }

  private boolean eligible(Player player) {
    if (player == null || !access.enabled()) {
      return false;
    }
    return gameplayEligible(true, player.isOnline(), player.getGameMode());
  }

  private boolean recoveryEligible(Player player) {
    return player != null && recoveryEligible(player.isOnline(), player.getGameMode());
  }

  static boolean gameplayEligible(boolean enabled, boolean online, GameMode gameMode) {
    return enabled && online && MutationWorldRuntime.eligibleGameMode(gameMode);
  }

  static boolean recoveryEligible(boolean online, GameMode gameMode) {
    return online && MutationWorldRuntime.eligibleGameMode(gameMode);
  }

  private boolean movedPosition(PlayerMoveEvent event) {
    if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) {
      return true;
    }
    double dx = event.getFrom().getX() - event.getTo().getX();
    double dy = event.getFrom().getY() - event.getTo().getY();
    double dz = event.getFrom().getZ() - event.getTo().getZ();
    return (dx * dx) + (dy * dy) + (dz * dz) > 0.0004D;
  }

  private boolean equivalent(MutationSnapshot previous, MutationSnapshot current) {
    if (previous == current) {
      return true;
    }
    if (previous == null || current == null) {
      return false;
    }
    return Objects.equals(previous.slotOneId(), current.slotOneId())
        && Objects.equals(previous.slotTwoId(), current.slotTwoId())
        && previous.expressed().equals(current.expressed())
        && previous.perfect() == current.perfect()
        && previous.cooperativeOptIn() == current.cooperativeOptIn();
  }

  private boolean isEnvironmentalFire(EntityDamageEvent.DamageCause cause) {
    return cause == EntityDamageEvent.DamageCause.FIRE
        || cause == EntityDamageEvent.DamageCause.FIRE_TICK
        || cause == EntityDamageEvent.DamageCause.LAVA
        || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
        || cause == EntityDamageEvent.DamageCause.CAMPFIRE;
  }
}
