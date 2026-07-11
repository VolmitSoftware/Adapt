package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.mutation.MutationClaim;
import art.arcane.adapt.api.mutation.MutationConfig;
import art.arcane.adapt.api.mutation.MutationEventClaims;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class MutationMovementRuntime {
  private static final int BASTION_INSPECTION_MULTIPLIER = 4;
  private static final long GALE_PROJECTILE_RESERVATION_MILLIS = 30_000L;
  private static final double MOVEMENT_EPSILON_SQUARED = 0.0004D;
  private static final long KNOCKBACK_WINDOW_MILLIS = 150L;
  private static final int PARADOX_PULSE_TICKS = 10;
  private static final long PARADOX_AUTHORIZATION_MILLIS = 1_000L;

  private final MutationRuntimeAccess access;
  private final MutationRuntimeStore store;
  private final MutationEntityResolver resolver;

  MutationMovementRuntime(MutationRuntimeAccess access, MutationRuntimeStore store, MutationEntityResolver resolver) {
    this.access = access;
    this.store = store;
    this.resolver = resolver;
  }

  void onMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    Location to = event.getTo();
    if (to == null || to.getWorld() == null) {
      return;
    }
    if (access.expressed(player, MutationType.GALE_LUNG)) {
      updateGale(player, event.getFrom(), to);
    }
    if (access.expressed(player, MutationType.BASTION_SPINE)) {
      updateBastion(player, event);
    }
    if (access.expressed(player, MutationType.PARADOX_SCAR)
        && event.getFrom().getWorld() == to.getWorld()
        && event.getFrom().distanceSquared(to) >= square(access.config().getParadoxScar().getMinimumDistance())) {
      createParadoxEcho(player, event.getFrom());
    }
  }

  void enforceBastionMovement(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    Location to = event.getTo();
    if (to == null || access.perfect(player) || !access.expressed(player, MutationType.BASTION_SPINE)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    Vector movement = to.toVector().subtract(event.getFrom().toVector());
    synchronized (runtime) {
      if (!runtime.bastion.anchored || (!player.isSprinting() && movement.getY() <= 0.08D)) {
        return;
      }
    }
    event.setTo(event.getFrom());
    player.setSprinting(false);
  }

  void onToggleSprint(PlayerToggleSprintEvent event) {
    if (!event.isSprinting() || !access.expressed(event.getPlayer(), MutationType.BASTION_SPINE)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(event.getPlayer().getUniqueId());
    synchronized (runtime) {
      if (runtime.bastion.anchored && !access.perfect(event.getPlayer())) {
        event.setCancelled(true);
      }
    }
  }

  void onInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    if (!access.expressed(player, MutationType.GALE_LUNG)) {
      return;
    }
    ItemStack item = event.getItem();
    if ((item != null && item.getType().name().endsWith("SHIELD")) || player.isBlocking()) {
      clearGale(player.getUniqueId());
    }
  }

  void onVelocity(PlayerVelocityEvent event) {
    Player player = event.getPlayer();
    if (!access.expressed(player, MutationType.GALE_LUNG) || access.perfect(player)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      if (runtime.gale.momentum < access.config().getGaleLung().getMaximumMomentum()
          || System.currentTimeMillis() - runtime.gale.lastIncomingHitAt > KNOCKBACK_WINDOW_MILLIS) {
        return;
      }
      Vector amplified = event.getVelocity().clone().multiply(access.config().getGaleLung().getBurdenKnockbackMultiplier());
      if (amplified.lengthSquared() > 9D) {
        amplified.normalize().multiply(3D);
      }
      event.setVelocity(amplified);
    }
  }

  void onProjectileLaunch(ProjectileLaunchEvent event) {
    if (!(event.getEntity() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player player)) {
      return;
    }
    if (!access.expressed(player, MutationType.GALE_LUNG)) {
      return;
    }
    MutationConfig.GaleLung config = access.config().getGaleLung();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    long now = System.currentTimeMillis();
    UUID playerId = player.getUniqueId();
    UUID projectileId = projectile.getUniqueId();
    synchronized (runtime) {
      if (runtime.gale.reservedProjectile != null && runtime.gale.reservedProjectileExpiresAt <= now) {
        runtime.gale.reservedProjectile = null;
        runtime.gale.reservedProjectileExpiresAt = 0L;
      }
      if (runtime.gale.momentum < config.getMaximumMomentum() || runtime.gale.reservedProjectile != null) {
        return;
      }
      Vector inherited = runtime.gale.lastMovement.clone();
      if (inherited.lengthSquared() > 0D) {
        inherited.normalize().multiply(config.getProjectileDisplacement());
        projectile.setVelocity(projectile.getVelocity().add(inherited));
      }
      runtime.gale.reservedProjectile = projectileId;
      runtime.gale.reservedProjectileExpiresAt = now + GALE_PROJECTILE_RESERVATION_MILLIS;
    }
    int expiryTicks = Math.max(1, (int) (GALE_PROJECTILE_RESERVATION_MILLIS / 50L));
    J.runEntity(player, () -> expireGaleProjectile(playerId, projectileId), expiryTicks);
  }

  void onProjectileHit(ProjectileHitEvent event) {
    Projectile projectile = event.getEntity();
    if (!(projectile.getShooter() instanceof Player player)) {
      return;
    }
    UUID playerId = player.getUniqueId();
    UUID projectileId = projectile.getUniqueId();
    J.runEntity(player, () -> {
      MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(playerId);
      if (runtime == null) {
        return;
      }
      synchronized (runtime) {
        if (projectileId.equals(runtime.gale.reservedProjectile)) {
          runtime.gale.reservedProjectile = null;
          runtime.gale.reservedProjectileExpiresAt = 0L;
        }
      }
    }, 1);
  }

  void onTeleport(PlayerTeleportEvent event) {
    Player player = event.getPlayer();
    Location to = event.getTo();
    if (to == null || to.getWorld() == null || !access.expressed(player, MutationType.PARADOX_SCAR)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      if (runtime.paradox.returning) {
        runtime.paradox.returning = false;
        return;
      }
    }
    if (event.getFrom().getWorld() != to.getWorld()) {
      clearParadox(player.getUniqueId());
      return;
    }
    if (event.getFrom().distanceSquared(to) >= square(access.config().getParadoxScar().getMinimumDistance())) {
      createParadoxEcho(player, event.getFrom());
    }
  }

  void onSwapHands(PlayerSwapHandItemsEvent event) {
    Player player = event.getPlayer();
    if (!player.isSneaking() || !access.expressed(player, MutationType.PARADOX_SCAR)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    Location origin;
    long generation;
    long loadoutGeneration;
    long expiresAt;
    synchronized (runtime) {
      origin = runtime.paradox.origin == null ? null : runtime.paradox.origin.clone();
      if (origin == null || runtime.paradox.returning || runtime.paradox.expiresAt <= System.currentTimeMillis()) {
        return;
      }
      generation = runtime.paradox.generation;
      loadoutGeneration = runtime.loadoutGeneration;
      expiresAt = runtime.paradox.expiresAt;
      runtime.paradox.returning = true;
    }
    MutationConfig.ParadoxScar config = access.config().getParadoxScar();
    if (origin.getWorld() != player.getWorld()
        || player.getLocation().distanceSquared(origin) > square(config.getMaximumReturnDistance())
        || !access.protection().canOccupy(player, player.getLocation())
        || !access.protection().canOccupy(player, origin)) {
      clearParadox(player.getUniqueId());
      return;
    }
    event.setCancelled(true);
    ParadoxReturnRequest request = new ParadoxReturnRequest(
        player.getUniqueId(),
        origin,
        generation,
        loadoutGeneration,
        expiresAt
    );
    if (!J.runAt(origin, () -> validateParadoxReturnDestination(request))) {
      clearParadoxReturnLatch(player.getUniqueId());
    }
  }

  void onDamage(EntityDamageByEntityEvent event, MutationEventClaims claims) {
    if (event.isCancelled() || event.getFinalDamage() <= 0D) {
      return;
    }
    if (collapseMarker(event)) {
      return;
    }
    Player attacker = resolver.playerSource(event.getDamager());
    UUID projectileAttackerId = resolver.projectilePlayerSourceId(event.getDamager());
    LivingEntity target = event.getEntity() instanceof LivingEntity living ? living : null;
    if (target instanceof Player victim) {
      boolean playerCombat = (attacker != null && attacker != victim) || projectileAttackerId != null;
      if (!playerCombat || access.pvpEnabled(MutationType.GALE_LUNG)) {
        markIncomingKnockback(victim);
      }
      if (!playerCombat || access.pvpEnabled(MutationType.BASTION_SPINE)) {
        absorbBastion(victim, resolver.sourceEntity(event.getDamager()), event.getFinalDamage());
      }
    }
    if (projectileAttackerId != null && event.getDamager() instanceof Projectile projectile) {
      queueProjectileGaleSpend(
          projectileAttackerId,
          projectile.getUniqueId(),
          target instanceof Player,
          claims
      );
    }
    if (attacker == null || target == null || !access.protection().canAffect(attacker, target)) {
      return;
    }
    if (access.expressed(attacker, MutationType.GALE_LUNG)
        && (!(target instanceof Player) || access.pvpEnabled(MutationType.GALE_LUNG))) {
      spendGale(attacker, target, event.getDamager(), claims);
    }
    if (access.expressed(attacker, MutationType.BASTION_SPINE)
        && (!(target instanceof Player) || access.pvpEnabled(MutationType.BASTION_SPINE))) {
      releaseBastion(attacker, target, claims);
    }
  }

  void cleanup(Player player) {
    if (player == null) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    removeParadoxMarker(runtime);
    synchronized (runtime) {
      runtime.gale.clear();
      runtime.bastion.clear();
      runtime.paradox.clear();
    }
  }

  private void updateGale(Player player, Location from, Location to) {
    MutationConfig.GaleLung config = access.config().getGaleLung();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    Vector movement = to.toVector().subtract(from.toVector());
    double distance = movement.length();
    long now = System.currentTimeMillis();
    boolean scheduleVent = false;
    long ventGeneration = 0L;
    synchronized (runtime) {
      if (player.isBlocking() || runtime.bastion.anchored) {
        runtime.gale.clear();
        return;
      }
      if (movement.lengthSquared() <= MOVEMENT_EPSILON_SQUARED) {
        return;
      }
      runtime.gale.lastMovementAt = now;
      double gained = 0D;
      if (player.isSprinting()) {
        gained += horizontalLength(movement) * config.getSprintMomentumPerBlock();
      }
      if (!player.isOnGround()) {
        gained += distance * config.getAirborneMomentumPerBlock();
      }
      if (gained > 0D) {
        runtime.gale.momentum = Math.min(config.getMaximumMomentum(), runtime.gale.momentum + gained);
        runtime.gale.lastPurposefulAt = now;
      }
      runtime.gale.lastMovement = movement.clone();
      runtime.gale.lastLocation = to.clone();
      if (runtime.gale.momentum > 0D && !runtime.gale.ventScheduled) {
        runtime.gale.ventScheduled = true;
        scheduleVent = true;
        ventGeneration = runtime.gale.ventGeneration;
      }
    }
    if (scheduleVent) {
      scheduleGaleVent(player, ventGeneration, config.getStationaryVentMillis());
    }
  }

  private void updateBastion(Player player, PlayerMoveEvent event) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    Location to = event.getTo();
    Vector movement = to.toVector().subtract(event.getFrom().toVector());
    long now = System.currentTimeMillis();
    boolean scheduleAnchor = false;
    long anchorGeneration = 0L;
    synchronized (runtime) {
      if (!eligibleBastionPosture(player)) {
        runtime.bastion.clear();
        return;
      }
      if (runtime.bastion.anchored) {
        runtime.gale.clear();
        return;
      }
      if (horizontalLengthSquared(movement) > MOVEMENT_EPSILON_SQUARED || movement.getY() > 0.08D || player.isSprinting()) {
        runtime.bastion.stillLocation = to.clone();
        runtime.bastion.stillSince = now;
      } else if (runtime.bastion.stillSince == 0L) {
        runtime.bastion.stillSince = now;
        runtime.bastion.stillLocation = to.clone();
      }
      if (!runtime.bastion.anchorScheduled) {
        runtime.bastion.anchorScheduled = true;
        scheduleAnchor = true;
        anchorGeneration = runtime.bastion.anchorGeneration;
      }
    }
    if (scheduleAnchor) {
      scheduleBastionAnchor(player, anchorGeneration, access.config().getBastionSpine().getAnchorChargeMillis());
    }
  }

  private void scheduleGaleVent(Player player, long generation, long delayMillis) {
    int delayTicks = Math.max(1, (int) Math.min(Integer.MAX_VALUE, (Math.max(1L, delayMillis) + 49L) / 50L));
    J.runEntity(player, () -> ventGale(player, generation), delayTicks);
  }

  private void ventGale(Player player, long generation) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    if (!access.expressed(player, MutationType.GALE_LUNG)) {
      clearGale(player.getUniqueId());
      return;
    }
    long remaining = 0L;
    boolean reschedule = false;
    synchronized (runtime) {
      if (runtime.gale.ventGeneration != generation) {
        return;
      }
      runtime.gale.ventScheduled = false;
      if (runtime.gale.momentum <= 0D) {
        return;
      }
      if (player.isBlocking() || runtime.bastion.anchored) {
        runtime.gale.clear();
        return;
      }
      long elapsed = System.currentTimeMillis() - runtime.gale.lastMovementAt;
      long stationaryMillis = access.config().getGaleLung().getStationaryVentMillis();
      if (elapsed >= stationaryMillis) {
        runtime.gale.clear();
        return;
      }
      remaining = stationaryMillis - elapsed;
      runtime.gale.ventScheduled = true;
      reschedule = true;
    }
    if (reschedule) {
      scheduleGaleVent(player, generation, remaining);
    }
  }

  private void expireGaleProjectile(UUID playerId, UUID projectileId) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(playerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (!projectileId.equals(runtime.gale.reservedProjectile)
          || runtime.gale.reservedProjectileExpiresAt > System.currentTimeMillis()) {
        return;
      }
      runtime.gale.reservedProjectile = null;
      runtime.gale.reservedProjectileExpiresAt = 0L;
    }
  }

  private void scheduleBastionAnchor(Player player, long generation, long delayMillis) {
    int delayTicks = Math.max(1, (int) Math.min(Integer.MAX_VALUE, (Math.max(1L, delayMillis) + 49L) / 50L));
    J.runEntity(player, () -> finishBastionAnchor(player, generation), delayTicks);
  }

  private void finishBastionAnchor(Player player, long generation) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    if (!access.expressed(player, MutationType.BASTION_SPINE) || !eligibleBastionPosture(player)) {
      synchronized (runtime) {
        runtime.bastion.clear();
      }
      return;
    }
    long remaining = 0L;
    boolean reschedule = false;
    boolean anchored = false;
    synchronized (runtime) {
      if (runtime.bastion.anchorGeneration != generation) {
        return;
      }
      runtime.bastion.anchorScheduled = false;
      if (runtime.bastion.anchored) {
        return;
      }
      Location current = player.getLocation();
      long now = System.currentTimeMillis();
      if (player.isSprinting() || runtime.bastion.stillLocation == null
          || runtime.bastion.stillLocation.getWorld() != current.getWorld()
          || runtime.bastion.stillLocation.distanceSquared(current) > MOVEMENT_EPSILON_SQUARED) {
        runtime.bastion.stillLocation = current;
        runtime.bastion.stillSince = now;
        remaining = access.config().getBastionSpine().getAnchorChargeMillis();
        runtime.bastion.anchorScheduled = true;
        reschedule = true;
      } else {
        long elapsed = now - runtime.bastion.stillSince;
        long chargeMillis = access.config().getBastionSpine().getAnchorChargeMillis();
        if (elapsed < chargeMillis) {
          remaining = chargeMillis - elapsed;
          runtime.bastion.anchorScheduled = true;
          reschedule = true;
        } else {
          runtime.bastion.anchored = true;
          runtime.gale.clear();
          anchored = true;
        }
      }
    }
    if (reschedule) {
      scheduleBastionAnchor(player, generation, remaining);
    } else if (anchored) {
      access.tell(player, MutationType.BASTION_SPINE, Particle.WAX_ON, 8);
    }
  }

  private boolean eligibleBastionPosture(Player player) {
    return player.isOnGround() && !player.isFlying() && !player.isGliding() && !player.isSwimming()
        && !player.getLocation().getBlock().isLiquid();
  }

  private void markIncomingKnockback(Player victim) {
    if (!access.expressed(victim, MutationType.GALE_LUNG)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(victim.getUniqueId());
    synchronized (runtime) {
      runtime.gale.lastIncomingHitAt = System.currentTimeMillis();
    }
  }

  private void absorbBastion(Player victim, Entity source, double damage) {
    if (!access.expressed(victim, MutationType.BASTION_SPINE)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(victim.getUniqueId());
    synchronized (runtime) {
      if (!runtime.bastion.anchored) {
        return;
      }
      if (source != null && isRearAttack(victim, source)) {
        runtime.bastion.clear();
        return;
      }
      MutationConfig.BastionSpine config = access.config().getBastionSpine();
      runtime.bastion.stability = Math.min(
          config.getMaximumStability(),
          runtime.bastion.stability + (damage * config.getStabilityPerDamage())
      );
    }
  }

  private void spendGale(Player attacker, LivingEntity target, Entity damager, MutationEventClaims claims) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(attacker.getUniqueId());
    MutationConfig.GaleLung config = access.config().getGaleLung();
    boolean projectile = damager instanceof Projectile;
    Location destination = projectile ? null : flankDestination(attacker, target, config.getMeleeFlankDistance());
    if (!projectile && destination == null) {
      return;
    }
    long now = System.currentTimeMillis();
    synchronized (runtime) {
      boolean projectileSpend = projectile
          && damager.getUniqueId().equals(runtime.gale.reservedProjectile)
          && runtime.gale.reservedProjectileExpiresAt > now;
      if (runtime.gale.momentum < config.getMaximumMomentum()
          || (projectile && !projectileSpend)
          || !claims.tryClaim(MutationClaim.MOVEMENT)) {
        return;
      }
      runtime.gale.momentum = 0D;
      runtime.gale.reservedProjectile = null;
      runtime.gale.reservedProjectileExpiresAt = 0L;
    }
    if (destination != null) {
      J.teleport(attacker, destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
    access.tell(attacker, MutationType.GALE_LUNG, Particle.CLOUD, 12);
  }

  private void queueProjectileGaleSpend(
      UUID attackerId,
      UUID projectileId,
      boolean playerTarget,
      MutationEventClaims claims
  ) {
    Player attacker = access.onlinePlayer(attackerId);
    if (attacker == null) {
      return;
    }
    J.runEntity(attacker, () -> {
      if (!access.expressed(attacker, MutationType.GALE_LUNG)
          || (playerTarget && !access.pvpEnabled(MutationType.GALE_LUNG))) {
        return;
      }
      MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(attackerId);
      if (runtime == null) {
        return;
      }
      long now = System.currentTimeMillis();
      synchronized (runtime) {
        if (runtime.gale.momentum < access.config().getGaleLung().getMaximumMomentum()
            || !projectileId.equals(runtime.gale.reservedProjectile)
            || runtime.gale.reservedProjectileExpiresAt <= now
            || !claims.tryClaim(MutationClaim.MOVEMENT)) {
          return;
        }
        runtime.gale.momentum = 0D;
        runtime.gale.reservedProjectile = null;
        runtime.gale.reservedProjectileExpiresAt = 0L;
      }
      access.tell(attacker, MutationType.GALE_LUNG, Particle.CLOUD, 12);
    });
  }

  private void releaseBastion(Player attacker, LivingEntity primaryTarget, MutationEventClaims claims) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(attacker.getUniqueId());
    double stability;
    long generation;
    long loadoutGeneration;
    synchronized (runtime) {
      if (!runtime.bastion.anchored || runtime.bastion.stability <= 0D || !isForceItem(attacker.getInventory().getItemInMainHand())
          || !claims.tryClaim(MutationClaim.POSTURE)) {
        return;
      }
      stability = runtime.bastion.stability;
      runtime.bastion.stability = 0D;
      runtime.bastion.anchorGeneration++;
      generation = runtime.bastion.anchorGeneration;
      loadoutGeneration = runtime.loadoutGeneration;
    }
    MutationConfig.BastionSpine config = access.config().getBastionSpine();
    Location origin = attacker.getLocation().clone();
    Vector facing = attacker.getEyeLocation().getDirection().setY(0D);
    if (facing.lengthSquared() <= 0D) {
      return;
    }
    facing.normalize();
    int inspectionLimit = Math.max(config.getMaximumTargets(), config.getMaximumTargets() * BASTION_INSPECTION_MULTIPLIER);
    AtomicInteger admitted = new AtomicInteger();
    BoundingBox bounds = BoundingBox.of(origin, config.getWaveRange(), config.getWaveRange(), config.getWaveRange());
    Collection<Entity> nearby = attacker.getWorld().getNearbyEntities(bounds, candidate -> {
      if (!(candidate instanceof LivingEntity) || candidate == attacker || candidate == primaryTarget) {
        return false;
      }
      return admitted.getAndIncrement() < inspectionLimit;
    });
    BastionWaveRequest request = new BastionWaveRequest(
        attacker.getUniqueId(),
        origin,
        facing,
        generation,
        loadoutGeneration,
        config.getWaveRange(),
        config.getWaveAngleDegrees(),
        Math.min(
            config.getMaximumVelocity(),
            0.2D + (stability / config.getMaximumStability()) * config.getMaximumVelocity()
        )
    );
    int dispatched = J.runEntity(primaryTarget, () -> stageBastionTarget(primaryTarget, request)) ? 1 : 0;
    for (Entity candidate : nearby) {
      if (dispatched >= config.getMaximumTargets()) {
        break;
      }
      LivingEntity living = (LivingEntity) candidate;
      if (J.runEntity(living, () -> stageBastionTarget(living, request))) {
        dispatched++;
      }
    }
    access.tell(attacker, MutationType.BASTION_SPINE, Particle.WAX_ON, 10);
  }

  private void stageBastionTarget(LivingEntity target, BastionWaveRequest request) {
    if (!target.isValid() || target.isDead() || target.getWorld() != request.origin().getWorld()
        || (target instanceof Player && !access.pvpEnabled(MutationType.BASTION_SPINE))) {
      return;
    }
    Location targetLocation = target.getLocation().clone();
    Vector direction = targetLocation.toVector().subtract(request.origin().toVector()).setY(0D);
    if (direction.lengthSquared() <= 0D
        || direction.lengthSquared() > square(request.range())
        || !withinAngle(request.facing(), direction, request.angleDegrees())) {
      return;
    }
    Vector velocity = direction.normalize().multiply(request.maximumVelocity());
    velocity.setY(Math.min(0.3D, velocity.length() * 0.3D));
    UUID targetId = target.getUniqueId();
    Player attacker = access.onlinePlayer(request.actorId());
    if (attacker == null) {
      return;
    }
    J.runEntity(attacker, () -> authorizeBastionTarget(
        attacker,
        target,
        targetId,
        targetLocation,
        velocity,
        request
    ));
  }

  private void authorizeBastionTarget(
      Player attacker,
      LivingEntity target,
      UUID targetId,
      Location targetLocation,
      Vector velocity,
      BastionWaveRequest request
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.actorId());
    if (runtime == null || !attacker.isOnline() || !access.expressed(attacker, MutationType.BASTION_SPINE)
        || (target instanceof Player && !access.pvpEnabled(MutationType.BASTION_SPINE))
        || !access.protection().canAffectAt(attacker, targetLocation, target instanceof Player)) {
      return;
    }
    synchronized (runtime) {
      if (runtime.loadoutGeneration != request.loadoutGeneration()
          || runtime.bastion.anchorGeneration != request.generation()) {
        return;
      }
    }
    BastionTargetAuthorization authorization = new BastionTargetAuthorization(
        request,
        targetId,
        targetLocation.clone(),
        velocity.clone(),
        System.currentTimeMillis()
    );
    J.runEntity(target, () -> applyBastionTarget(target, authorization));
  }

  private void applyBastionTarget(LivingEntity target, BastionTargetAuthorization authorization) {
    BastionWaveRequest request = authorization.request();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.actorId());
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (runtime.loadoutGeneration != request.loadoutGeneration()
          || runtime.bastion.anchorGeneration != request.generation()) {
        return;
      }
    }
    if (!target.isValid() || target.isDead()
        || !target.getUniqueId().equals(authorization.targetId())
        || !sameBlock(target.getLocation(), authorization.targetLocation())
        || System.currentTimeMillis() - authorization.authorizedAt() > PARADOX_AUTHORIZATION_MILLIS
        || (target instanceof Player && !access.pvpEnabled(MutationType.BASTION_SPINE))) {
      return;
    }
    target.setVelocity(authorization.velocity());
  }

  private void validateParadoxReturnDestination(ParadoxReturnRequest request) {
    Player player = access.onlinePlayer(request.ownerId());
    if (!isSafeDestination(request.origin())) {
      if (player != null) {
        J.runEntity(player, () -> rejectParadoxReturn(request, true));
      } else {
        clearParadox(request.ownerId());
      }
      return;
    }
    ParadoxReturnAuthorization authorization = new ParadoxReturnAuthorization(
        request,
        request.origin().clone(),
        System.currentTimeMillis()
    );
    if (player == null || !J.runEntity(player, () -> completeParadoxReturn(player, authorization))) {
      clearParadoxReturnLatch(request.ownerId());
    }
  }

  private void completeParadoxReturn(Player player, ParadoxReturnAuthorization authorization) {
    ParadoxReturnRequest request = authorization.request();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.ownerId());
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (runtime.loadoutGeneration != request.loadoutGeneration()
          || runtime.paradox.generation != request.generation()
          || runtime.paradox.expiresAt != request.expiresAt()
          || runtime.paradox.origin == null
          || !runtime.paradox.returning) {
        return;
      }
    }
    MutationConfig.ParadoxScar config = access.config().getParadoxScar();
    if (!player.isOnline()
        || !access.expressed(player, MutationType.PARADOX_SCAR)
        || request.expiresAt() <= System.currentTimeMillis()
        || System.currentTimeMillis() - authorization.authorizedAt() > PARADOX_AUTHORIZATION_MILLIS
        || player.getWorld() != authorization.destination().getWorld()
        || player.getLocation().distanceSquared(authorization.destination()) > square(config.getMaximumReturnDistance())
        || !access.protection().canOccupy(player, player.getLocation())
        || !access.protection().canOccupy(player, authorization.destination())) {
      rejectParadoxReturn(request, true);
      return;
    }
    if (!J.teleport(player, authorization.destination(), PlayerTeleportEvent.TeleportCause.PLUGIN)) {
      rejectParadoxReturn(request, false);
      return;
    }
    removeParadoxMarker(runtime);
    clearParadoxState(runtime);
    J.runEntity(player, () -> clearParadoxReturnLatch(player.getUniqueId()), 2);
    access.tell(player, MutationType.PARADOX_SCAR, Particle.REVERSE_PORTAL, 12);
  }

  private void rejectParadoxReturn(ParadoxReturnRequest request, boolean clearEcho) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.ownerId());
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (runtime.paradox.generation != request.generation()) {
        return;
      }
      runtime.paradox.returning = false;
    }
    if (clearEcho) {
      clearParadox(request.ownerId());
    }
  }

  private void createParadoxEcho(Player player, Location origin) {
    if (!isLoaded(origin) || !access.protection().canOccupy(player, origin)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    long generation;
    synchronized (runtime) {
      if (runtime.paradox.origin != null && runtime.paradox.expiresAt > System.currentTimeMillis()) {
        return;
      }
      runtime.paradox.origin = origin.clone();
      runtime.paradox.expiresAt = System.currentTimeMillis() + access.config().getParadoxScar().getEchoLifetimeMillis();
      runtime.paradox.generation++;
      generation = runtime.paradox.generation;
    }
    Location spawnLocation = origin.clone();
    J.runAt(spawnLocation, () -> spawnParadoxMarker(player.getUniqueId(), spawnLocation, generation));
    access.tell(player, MutationType.PARADOX_SCAR, Particle.REVERSE_PORTAL, 10);
  }

  private void spawnParadoxMarker(UUID ownerId, Location location, long generation) {
    Player owner = access.onlinePlayer(ownerId);
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (owner == null || runtime.paradox.generation != generation || runtime.paradox.origin == null || !isLoaded(location)) {
        return;
      }
    }
    ArmorStand marker = location.getWorld().spawn(location, ArmorStand.class, stand -> {
      stand.setVisible(false);
      stand.setGravity(false);
      stand.setSilent(true);
      stand.setSmall(true);
      stand.setBasePlate(false);
      stand.setArms(false);
      stand.setCanPickupItems(false);
      stand.setCollidable(false);
      stand.setPersistent(false);
      stand.setGlowing(true);
      stand.setCustomName(ChatColor.LIGHT_PURPLE + "Return Echo");
      stand.setCustomNameVisible(true);
      stand.setMetadata("NPC", new FixedMetadataValue(Adapt.instance, true));
      stand.setMetadata("adapt-mutation-marker", new FixedMetadataValue(Adapt.instance, true));
      stand.addScoreboardTag("adapt-mutation-marker");
    });
    synchronized (runtime) {
      if (runtime.paradox.generation != generation) {
        marker.remove();
        return;
      }
      runtime.paradox.marker = marker;
      store.paradoxMarkers.put(marker.getUniqueId(), ownerId);
    }
    int expiryTicks = Math.max(1, (int) (access.config().getParadoxScar().getEchoLifetimeMillis() / 50L));
    J.runEntity(owner, () -> expireParadox(ownerId, generation), expiryTicks);
    J.runEntity(marker, () -> pulseParadox(ownerId, marker, generation), PARADOX_PULSE_TICKS);
  }

  private boolean collapseMarker(EntityDamageByEntityEvent event) {
    UUID ownerId = store.paradoxMarkers.get(event.getEntity().getUniqueId());
    if (ownerId == null) {
      return false;
    }
    event.setCancelled(true);
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      store.paradoxMarkers.remove(event.getEntity().getUniqueId());
      event.getEntity().remove();
      return true;
    }
    Location origin;
    long generation;
    synchronized (runtime) {
      if (runtime.paradox.marker == null
          || !event.getEntity().getUniqueId().equals(runtime.paradox.marker.getUniqueId())
          || runtime.paradox.origin == null
          || runtime.paradox.expiresAt <= System.currentTimeMillis()) {
        return true;
      }
      origin = runtime.paradox.origin.clone();
      generation = runtime.paradox.generation;
    }
    Entity hostile = resolver.sourceEntity(event.getDamager());
    if (hostile == null) {
      return true;
    }
    Player directAttacker = resolver.playerSource(hostile);
    UUID projectileAttackerId = resolver.projectilePlayerSourceId(hostile);
    if (directAttacker != null) {
      if (directAttacker.getUniqueId().equals(ownerId)
          || !authorizeParadoxCollapse(directAttacker, origin, event.getEntity().getLocation())) {
        return true;
      }
      collapseParadoxMarker(ownerId, event.getEntity(), generation);
      return true;
    }
    if (projectileAttackerId != null) {
      if (projectileAttackerId.equals(ownerId)) {
        return true;
      }
      Player projectileAttacker = access.onlinePlayer(projectileAttackerId);
      if (projectileAttacker == null) {
        return true;
      }
      ParadoxCollapseRequest request = new ParadoxCollapseRequest(
          ownerId,
          event.getEntity().getUniqueId(),
          origin,
          event.getEntity().getLocation().clone(),
          generation
      );
      J.runEntity(projectileAttacker, () -> authorizeOwnedParadoxCollapse(
          projectileAttacker,
          event.getEntity(),
          request
      ));
      return true;
    }
    UUID controllingPlayerId = resolver.packOwnerId(hostile);
    if (controllingPlayerId != null) {
      if (controllingPlayerId.equals(ownerId)) {
        return true;
      }
      Player controllingPlayer = access.onlinePlayer(controllingPlayerId);
      if (controllingPlayer == null) {
        return true;
      }
      ParadoxCollapseRequest request = new ParadoxCollapseRequest(
          ownerId,
          event.getEntity().getUniqueId(),
          origin,
          event.getEntity().getLocation().clone(),
          generation
      );
      J.runEntity(controllingPlayer, () -> authorizeOwnedParadoxCollapse(
          controllingPlayer,
          event.getEntity(),
          request
      ));
      return true;
    }
    if (hostile instanceof LivingEntity) {
      collapseParadoxMarker(ownerId, event.getEntity(), generation);
    }
    return true;
  }

  private boolean authorizeParadoxCollapse(Player attacker, Location origin, Location markerLocation) {
    return access.pvpEnabled(MutationType.PARADOX_SCAR)
        && access.protection().canAffectAt(attacker, markerLocation, true)
        && access.protection().canAffectAt(attacker, origin, true);
  }

  private void authorizeOwnedParadoxCollapse(
      Player attacker,
      Entity marker,
      ParadoxCollapseRequest request
  ) {
    if (!attacker.isOnline() || !authorizeParadoxCollapse(attacker, request.origin(), request.markerLocation())) {
      return;
    }
    ParadoxCollapseAuthorization authorization = new ParadoxCollapseAuthorization(
        request,
        System.currentTimeMillis()
    );
    J.runEntity(marker, () -> acceptProjectileParadoxCollapse(marker, authorization));
  }

  private void acceptProjectileParadoxCollapse(Entity marker, ParadoxCollapseAuthorization authorization) {
    ParadoxCollapseRequest request = authorization.request();
    if (!marker.isValid()
        || !marker.getUniqueId().equals(request.markerId())
        || !sameBlock(marker.getLocation(), request.markerLocation())
        || System.currentTimeMillis() - authorization.authorizedAt() > PARADOX_AUTHORIZATION_MILLIS) {
      return;
    }
    collapseParadoxMarker(request.ownerId(), marker, request.generation());
  }

  private void collapseParadoxMarker(UUID ownerId, Entity marker, long generation) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      store.paradoxMarkers.remove(marker.getUniqueId());
      marker.remove();
      return;
    }
    synchronized (runtime) {
      if (runtime.paradox.generation != generation
          || runtime.paradox.marker == null
          || !marker.getUniqueId().equals(runtime.paradox.marker.getUniqueId())
          || runtime.paradox.origin == null
          || runtime.paradox.expiresAt <= System.currentTimeMillis()) {
        return;
      }
      runtime.paradox.marker = null;
      runtime.paradox.clear();
    }
    store.paradoxMarkers.remove(marker.getUniqueId(), ownerId);
    marker.remove();
    Player owner = access.onlinePlayer(ownerId);
    if (owner != null) {
      J.runEntity(owner, () -> applyParadoxCollapse(owner));
    }
  }

  private void pulseParadox(UUID ownerId, Entity marker, long generation) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      store.paradoxMarkers.remove(marker.getUniqueId());
      marker.remove();
      return;
    }
    synchronized (runtime) {
      if (runtime.paradox.generation != generation || runtime.paradox.marker == null
          || !marker.getUniqueId().equals(runtime.paradox.marker.getUniqueId())
          || runtime.paradox.origin == null || runtime.paradox.expiresAt <= System.currentTimeMillis()) {
        return;
      }
    }
    if (!marker.isValid() || !isLoaded(marker.getLocation())) {
      clearParadox(ownerId);
      return;
    }
    Fx.now(MutationType.PARADOX_SCAR, marker.getLocation().add(0D, 1D, 0D), FxPriority.AMBIENT)
        .burst(Particle.REVERSE_PORTAL, 5, 0.3D);
    J.runEntity(marker, () -> pulseParadox(ownerId, marker, generation), PARADOX_PULSE_TICKS);
  }

  private void applyParadoxCollapse(Player owner) {
    if (!owner.isOnline() || access.perfect(owner)) {
      return;
    }
    owner.addPotionEffect(new PotionEffect(
        PotionEffectType.NAUSEA,
        access.config().getParadoxScar().getHostileCollapseTicks(),
        0,
        true,
        false,
        true
    ));
  }

  private void expireParadox(UUID ownerId, long generation) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (runtime.paradox.generation != generation || runtime.paradox.expiresAt > System.currentTimeMillis()) {
        return;
      }
    }
    removeParadoxMarker(runtime);
    clearParadoxState(runtime);
  }

  private void clearGale(UUID playerId) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(playerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      runtime.gale.clear();
    }
  }

  private void clearParadox(UUID playerId) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(playerId);
    if (runtime == null) {
      return;
    }
    removeParadoxMarker(runtime);
    clearParadoxState(runtime);
  }

  private void removeParadoxMarker(MutationRuntimeStore.PlayerRuntimeState runtime) {
    Entity marker;
    synchronized (runtime) {
      marker = runtime.paradox.marker;
      runtime.paradox.marker = null;
    }
    if (marker != null) {
      store.paradoxMarkers.remove(marker.getUniqueId());
      J.runEntity(marker, marker::remove);
    }
  }

  private void clearParadoxState(MutationRuntimeStore.PlayerRuntimeState runtime) {
    synchronized (runtime) {
      boolean returning = runtime.paradox.returning;
      runtime.paradox.clear();
      runtime.paradox.returning = returning;
    }
  }

  private void clearParadoxReturnLatch(UUID playerId) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(playerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      runtime.paradox.returning = false;
    }
  }

  private Location flankDestination(Player attacker, LivingEntity target, double distance) {
    Vector forward = target.getLocation().getDirection().setY(0D);
    if (forward.lengthSquared() <= 0D) {
      return null;
    }
    Vector side = new Vector(-forward.getZ(), 0D, forward.getX()).normalize().multiply(distance);
    Vector attackerSide = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
    if (attackerSide.dot(side) < 0D) {
      side.multiply(-1D);
    }
    Location preferred = target.getLocation().add(side);
    preferred.setYaw(attacker.getLocation().getYaw());
    preferred.setPitch(attacker.getLocation().getPitch());
    if (isSafeFlank(attacker, preferred)) {
      return preferred;
    }
    Location alternate = target.getLocation().subtract(side);
    alternate.setYaw(attacker.getLocation().getYaw());
    alternate.setPitch(attacker.getLocation().getPitch());
    return isSafeFlank(attacker, alternate) ? alternate : null;
  }

  private boolean isSafeFlank(Player attacker, Location location) {
    if (!isSafeDestination(location) || !access.protection().canOccupy(attacker, location)) {
      return false;
    }
    Block support = location.clone().subtract(0D, 1D, 0D).getBlock();
    return support.getType().isSolid() && !support.isLiquid();
  }

  private boolean isSafeDestination(Location location) {
    if (!isLoaded(location) || location.getWorld() == null || !location.getWorld().getWorldBorder().isInside(location)
        || location.getY() < location.getWorld().getMinHeight()
        || location.getY() + 1D >= location.getWorld().getMaxHeight()) {
      return false;
    }
    Block feet = location.getBlock();
    return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable();
  }

  private boolean isRearAttack(Player victim, Entity attacker) {
    if (!attacker.isValid() || attacker.getWorld() != victim.getWorld()) {
      return false;
    }
    Vector facing = victim.getEyeLocation().getDirection().setY(0D);
    Vector toAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0D);
    if (facing.lengthSquared() <= 0D || toAttacker.lengthSquared() <= 0D) {
      return false;
    }
    return facing.normalize().dot(toAttacker.normalize()) < -0.35D;
  }

  private boolean isForceItem(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return true;
    }
    String name = item.getType().name();
    return name.endsWith("SHIELD") || name.endsWith("AXE") || name.endsWith("PICKAXE") || name.endsWith("SHOVEL")
        || name.endsWith("HOE") || item.getType().isBlock();
  }

  private boolean withinAngle(Vector facing, Vector direction, double fullAngleDegrees) {
    double dot = facing.clone().normalize().dot(direction.clone().normalize());
    double threshold = Math.cos(Math.toRadians(fullAngleDegrees / 2D));
    return dot >= threshold;
  }

  private boolean isLoaded(Location location) {
    return location != null
        && location.getWorld() != null
        && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  private double horizontalLength(Vector vector) {
    return Math.sqrt(horizontalLengthSquared(vector));
  }

  private double horizontalLengthSquared(Vector vector) {
    return (vector.getX() * vector.getX()) + (vector.getZ() * vector.getZ());
  }

  private double square(double value) {
    return value * value;
  }

  static boolean sameBlock(Location first, Location second) {
    return first != null
        && second != null
        && first.getWorld() != null
        && first.getWorld() == second.getWorld()
        && first.getBlockX() == second.getBlockX()
        && first.getBlockY() == second.getBlockY()
        && first.getBlockZ() == second.getBlockZ();
  }

  private record BastionWaveRequest(
      UUID actorId,
      Location origin,
      Vector facing,
      long generation,
      long loadoutGeneration,
      double range,
      double angleDegrees,
      double maximumVelocity
  ) {
  }

  private record BastionTargetAuthorization(
      BastionWaveRequest request,
      UUID targetId,
      Location targetLocation,
      Vector velocity,
      long authorizedAt
  ) {
  }

  private record ParadoxReturnRequest(
      UUID ownerId,
      Location origin,
      long generation,
      long loadoutGeneration,
      long expiresAt
  ) {
  }

  private record ParadoxReturnAuthorization(
      ParadoxReturnRequest request,
      Location destination,
      long authorizedAt
  ) {
  }

  private record ParadoxCollapseRequest(
      UUID ownerId,
      UUID markerId,
      Location origin,
      Location markerLocation,
      long generation
  ) {
  }

  private record ParadoxCollapseAuthorization(
      ParadoxCollapseRequest request,
      long authorizedAt
  ) {
  }
}
