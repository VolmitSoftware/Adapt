package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.api.mutation.MutationClaim;
import art.arcane.adapt.api.mutation.MutationConfig;
import art.arcane.adapt.api.mutation.MutationEventClaims;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class MutationCombatRuntime {
  private static final int FARM_DEATH_LIMIT = 6;
  private static final long FARM_WINDOW_MILLIS = 60_000L;
  private static final int FARM_PRUNE_BUDGET = 256;
  private static final int MAX_FARM_KEYS = 2_048;
  private static final int MAX_SPAWN_PROVENANCE = 16_384;
  private static final int MAX_QUARRY_OWNERS = 16;
  private static final int MAX_TROPHY_RECOGNITION_TARGETS = 12;
  private static final long PROTECTION_AUTHORIZATION_MILLIS = 1_000L;
  private static final long PACK_CONTRIBUTION_MILLIS = 5_000L;
  private static final int SPAWN_PRUNE_BUDGET = 256;
  private static final long TROPHY_CLEAR_CONFIRMATION_MILLIS = 5_000L;
  private static final long DEATH_MUTATION_GRANT_MILLIS = 5_000L;
  private static final int MAX_DEATH_MUTATION_GRANTS = 4_096;
  private static final int DEATH_GRANT_PRUNE_BUDGET = 256;
  private static final long TROPHY_RECOGNITION_INTERVAL_MILLIS = 1_000L;

  private final MutationRuntimeAccess access;
  private final MutationRuntimeStore store;
  private final MutationEntityResolver resolver;
  private final MutationBlockProvenance provenance;
  private final MutationWorldRuntime world;
  private final NamespacedKey trophyFamilyKey;
  private final Map<FarmKey, Deque<Long>> farmDeaths = new ConcurrentHashMap<>();
  private final Object farmPruneLock = new Object();
  private Iterator<Map.Entry<FarmKey, Deque<Long>>> farmPruneIterator;

  MutationCombatRuntime(
      MutationRuntimeAccess access,
      MutationRuntimeStore store,
      MutationEntityResolver resolver,
      MutationBlockProvenance provenance,
      MutationWorldRuntime world
  ) {
    this.access = access;
    this.store = store;
    this.resolver = resolver;
    this.provenance = provenance;
    this.world = world;
    trophyFamilyKey = new NamespacedKey(Adapt.instance, "mutation-trophy-family");
  }

  void onSpawn(CreatureSpawnEvent event) {
    if (!access.enabled()) {
      return;
    }
    CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
    boolean natural = reason == CreatureSpawnEvent.SpawnReason.NATURAL
        || reason == CreatureSpawnEvent.SpawnReason.JOCKEY
        || reason == CreatureSpawnEvent.SpawnReason.PATROL
        || reason == CreatureSpawnEvent.SpawnReason.RAID;
    if (store.spawnProvenance.size() >= MAX_SPAWN_PROVENANCE) {
      pruneSpawnProvenance();
      if (store.spawnProvenance.size() >= MAX_SPAWN_PROVENANCE) {
        return;
      }
    }
    store.spawnProvenance.put(event.getEntity().getUniqueId(), new MutationRuntimeStore.SpawnProvenance(
        natural,
        System.currentTimeMillis()
    ));
  }

  void onDamage(EntityDamageByEntityEvent event, MutationEventClaims claims) {
    if (!access.enabled()
        || event.isCancelled()
        || event.getFinalDamage() <= 0D
        || !(event.getEntity() instanceof LivingEntity target)) {
      return;
    }
    UUID packOwnerId = resolver.packOwnerId(event.getDamager());
    if (packOwnerId == null || packOwnerId.equals(target.getUniqueId())) {
      return;
    }
    Player attacker = resolver.playerSource(event.getDamager());
    if (attacker != null && !access.protection().canAffect(attacker, target)) {
      return;
    }
    UUID projectileAttackerId = resolver.projectilePlayerSourceId(event.getDamager());
    Player projectileAttacker = projectileAttackerId == null ? null : access.onlinePlayer(projectileAttackerId);
    UUID sourceEntityId = event.getDamager().getUniqueId();
    if (attacker != null) {
      stageDeathMutationGrant(
          target.getUniqueId(),
          sourceEntityId,
          attacker.getUniqueId(),
          target instanceof Player,
          access.snapshot(attacker)
      );
    } else if (projectileAttacker != null
        && (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(projectileAttacker))) {
      ProjectileDeathGrantRequest request = new ProjectileDeathGrantRequest(
          target.getUniqueId(),
          sourceEntityId,
          target.getLocation().clone(),
          target instanceof Player
      );
      authorizeProjectileDeathGrant(projectileAttacker, request);
    }
    applyPackContributions(event, attacker == null ? projectileAttacker : attacker, target);
    if (attacker == null) {
      if (projectileAttackerId != null && event.getDamager() instanceof Projectile projectile) {
        applyPackProjectileBurden(event, projectileAttackerId, target instanceof Player);
        queueProjectileMutationHit(
            projectileAttackerId,
            projectile.getLocation().clone(),
            target,
            target.getUniqueId(),
            target.getWorld().getUID(),
            target.getLocation().clone(),
            target instanceof Player,
            claims
        );
      }
      return;
    }
    for (MutationType type : access.ordered(attacker)) {
      if (target instanceof Player && !access.pvpEnabled(type)) {
        continue;
      }
      switch (type) {
        case PACKMIND -> handlePackOwnerHit(event, attacker, target);
        case TROPHY_CRUCIBLE -> spendTrophy(attacker, target, claims);
        case ARSENAL_CORTEX -> handleArsenal(attacker, target, event.getDamager(), claims);
        case UMBRAL_ECHO -> handleUmbral(attacker, target, event.getDamager(), claims);
        default -> {
        }
      }
    }
  }

  void onDeath(EntityDeathEvent event) {
    LivingEntity dead = event.getEntity();
    MutationRuntimeStore.SpawnProvenance spawn = store.spawnProvenance.remove(dead.getUniqueId());
    MutationRuntimeStore.DeathMutationGrant grant = store.deathMutationGrants.remove(dead.getUniqueId());
    clearQuarry(dead.getUniqueId());
    if (!access.enabled() || !validDeathMutationGrant(dead, grant) || !eligibleNaturalDeath(dead, spawn)) {
      return;
    }
    String family = familyOf(dead);
    if (isFarmed(dead, family)) {
      return;
    }
    if (grant.trophyCrucible()) {
      tagNaturalTrophy(event.getDrops(), family);
    }
    if (grant.gravebloom()) {
      Location bloomLocation = dead.getLocation();
      Block terrain = bloomLocation.getBlock();
      if (terrain.getType().isAir()) {
        bloomLocation = bloomLocation.clone().subtract(0D, 1D, 0D);
      }
      world.createGravebloom(grant.ownerId(), grant.loadoutGeneration(), bloomLocation);
    }
  }

  void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND
        || event.getAction() != Action.RIGHT_CLICK_BLOCK
        || !event.getPlayer().isSneaking()
        || event.getClickedBlock() == null
        || event.getClickedBlock().getType() != Material.CRAFTING_TABLE
        || !access.expressed(event.getPlayer(), MutationType.TROPHY_CRUCIBLE)
        || !access.protection().canInteract(event.getPlayer(), event.getClickedBlock().getLocation())) {
      return;
    }
    Player player = event.getPlayer();
    PlayerMutationData durable = access.durable(player);
    if (durable == null) {
      return;
    }
    long now = System.currentTimeMillis();
    clearExpiredTrophy(player, durable, now);
    ItemStack item = event.getItem();
    String family = trophyFamily(item);
    if (family == null) {
      if (item != null && !item.getType().isAir()) {
        return;
      }
      clearTrophyAtStation(event, player, durable, now);
      return;
    }
    if (!durable.getTrophyImprint().isBlank() && durable.getTrophyImprintExpiresAt() > now) {
      return;
    }
    durable.setTrophyImprint(family);
    long expiresAt = now + access.config().getTrophyCrucible().getImprintLifetimeMillis();
    durable.setTrophyImprintExpiresAt(expiresAt);
    item.setAmount(item.getAmount() - 1);
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      runtime.trophyClearConfirmUntil = 0L;
      runtime.trophy.clear();
    }
    access.save(player);
    startTrophyRecognition(player);
    scheduleTrophyExpiry(player, family, expiresAt);
    event.setCancelled(true);
    access.tell(player, MutationType.TROPHY_CRUCIBLE, Particle.ENCHANT, 10);
  }

  void onTarget(EntityTargetLivingEntityEvent event) {
    if (!access.enabled()
        || event.isCancelled()
        || !(event.getTarget() instanceof Player player)
        || !(event.getEntity() instanceof Mob mob)) {
      return;
    }
    String family = familyOf(mob);
    Location mobLocation = mob.getLocation().clone();
    J.runEntity(player, () -> {
      if (!access.expressed(player, MutationType.TROPHY_CRUCIBLE) || access.perfect(player)) {
        return;
      }
      PlayerMutationData durable = access.durable(player);
      if (durable == null || clearExpiredTrophy(player, durable, System.currentTimeMillis())
          || !family.equals(durable.getTrophyImprint())) {
        return;
      }
      startTrophyRecognition(player);
      double range = access.config().getTrophyCrucible().getRecognitionRange();
      if (player.getWorld() != mobLocation.getWorld()
          || player.getLocation().distanceSquared(mobLocation) > range * range) {
        return;
      }
      Location playerLocation = player.getLocation().clone();
      J.runEntity(mob, () -> maintainTrophyPursuit(player, mob, family, playerLocation, range));
    });
  }

  void onMove(Player player) {
    startTrophyRecognition(player);
  }

  private void startTrophyRecognition(Player player) {
    if (!access.expressed(player, MutationType.TROPHY_CRUCIBLE) || access.perfect(player)) {
      return;
    }
    PlayerMutationData durable = access.durable(player);
    long now = System.currentTimeMillis();
    if (durable == null || clearExpiredTrophy(player, durable, now) || durable.getTrophyImprint().isBlank()) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    long recognitionGeneration;
    synchronized (runtime) {
      if (runtime.trophy.recognitionScheduled) {
        return;
      }
      runtime.trophy.recognitionScheduled = true;
      runtime.trophy.recognitionGeneration++;
      recognitionGeneration = runtime.trophy.recognitionGeneration;
    }
    recognizeTrophyFamily(player, recognitionGeneration);
  }

  private void recognizeTrophyFamily(Player player, long recognitionGeneration) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    long loadoutGeneration;
    synchronized (runtime) {
      if (!runtime.trophy.recognitionScheduled
          || runtime.trophy.recognitionGeneration != recognitionGeneration) {
        return;
      }
      runtime.trophy.nextRecognitionAt = System.currentTimeMillis() + TROPHY_RECOGNITION_INTERVAL_MILLIS;
      loadoutGeneration = runtime.loadoutGeneration;
    }
    if (!access.expressed(player, MutationType.TROPHY_CRUCIBLE) || access.perfect(player)) {
      stopTrophyRecognition(runtime, recognitionGeneration);
      return;
    }
    PlayerMutationData durable = access.durable(player);
    long now = System.currentTimeMillis();
    if (durable == null || clearExpiredTrophy(player, durable, now) || durable.getTrophyImprint().isBlank()) {
      stopTrophyRecognition(runtime, recognitionGeneration);
      return;
    }
    String family = durable.getTrophyImprint();
    Location playerLocation = player.getLocation().clone();
    double range = access.config().getTrophyCrucible().getRecognitionRange();
    AtomicInteger admitted = new AtomicInteger();
    BoundingBox bounds = BoundingBox.of(playerLocation, range, range, range);
    Collection<Entity> nearby = player.getWorld().getNearbyEntities(bounds, candidate -> {
      if (!(candidate instanceof Mob) || candidate == player) {
        return false;
      }
      return admitted.getAndIncrement() < MAX_TROPHY_RECOGNITION_TARGETS;
    });
    for (Entity entity : nearby) {
      Mob mob = (Mob) entity;
      J.runEntity(mob, () -> {
        MutationRuntimeStore.PlayerRuntimeState current = store.existing(player.getUniqueId());
        if (current == null) {
          return;
        }
        synchronized (current) {
          if (current.loadoutGeneration != loadoutGeneration) {
            return;
          }
        }
        maintainTrophyPursuit(player, mob, family, playerLocation, range);
      });
    }
    int delayTicks = Math.max(1, (int) ((TROPHY_RECOGNITION_INTERVAL_MILLIS + 49L) / 50L));
    if (!J.runEntity(player, () -> recognizeTrophyFamily(player, recognitionGeneration), delayTicks)) {
      stopTrophyRecognition(runtime, recognitionGeneration);
    }
  }

  private void stopTrophyRecognition(
      MutationRuntimeStore.PlayerRuntimeState runtime,
      long recognitionGeneration
  ) {
    synchronized (runtime) {
      if (runtime.trophy.recognitionGeneration != recognitionGeneration) {
        return;
      }
      runtime.trophy.recognitionScheduled = false;
      runtime.trophy.nextRecognitionAt = 0L;
      runtime.trophy.recognitionGeneration++;
    }
  }

  boolean applyUtility(
      Player actor,
      LivingEntity target,
      MutationUtilityTag tag,
      double factor,
      MutationEventClaims claims,
      boolean echo
  ) {
    if (actor == null || target == null || tag == null || tag == MutationUtilityTag.NONE
        || !access.protection().canAffect(actor, target)) {
      return false;
    }
    if (echo) {
      if (claims == null || !claims.tryClaim(MutationClaim.UTILITY_ECHO)) {
        return false;
      }
      MutationRuntimeStore.PlayerRuntimeState runtime = store.player(actor.getUniqueId());
      if (!runtime.tryClaimUtilityEcho(System.currentTimeMillis())) {
        return false;
      }
    }
    double boundedFactor = MutationRuntimePolicy.clamp(factor, 0.1D, 1D);
    Location actorOrigin = actor.getLocation().clone();
    J.runEntity(target, () -> applyUtilityOwned(actorOrigin, target, tag, boundedFactor));
    return true;
  }

  void applyUtilityOwned(Location actorOrigin, LivingEntity target, MutationUtilityTag tag, double factor) {
    if (actorOrigin == null || actorOrigin.getWorld() == null || target == null || !target.isValid() || target.isDead()
        || target.getWorld() != actorOrigin.getWorld() || tag == null || tag == MutationUtilityTag.NONE) {
      return;
    }
    int shortDuration = Math.max(5, (int) Math.round(40D * factor));
    switch (tag) {
      case PRECISION, MARKING -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, shortDuration, 0, true, false, true));
      case POSTURE_PRESSURE, INTERRUPTION -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, shortDuration, 0, true, false, true));
      case PINNING -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 1, true, false, true));
      case DISPLACEMENT -> {
        Vector away = target.getLocation().toVector().subtract(actorOrigin.toVector()).setY(0D);
        if (away.lengthSquared() > 0D) {
          target.setVelocity(away.normalize().multiply(Math.min(0.55D, 0.25D + (factor * 0.3D))).setY(0.12D));
        }
      }
      case DEBUFF, REGENERATION_SUPPRESSION -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, shortDuration, 1, true, false, true));
      case BUOYANCY -> target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, Math.max(5, shortDuration / 2), 0, true, false, true));
      case HEAT -> target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + shortDuration));
      case TERRAIN_PRESERVATION, NONE -> {
      }
    }
  }

  void cleanup(Player player) {
    if (player == null) {
      return;
    }
    clearPack(player.getUniqueId());
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime == null) {
      return;
    }
    List<UUID> exposedViewers;
    synchronized (runtime) {
      exposedViewers = List.copyOf(runtime.umbral.exposedViewers.keySet());
      runtime.arsenal.clear();
      runtime.pack.clear();
      runtime.umbral.clear();
      runtime.trophyClearConfirmUntil = 0L;
    }
    player.removeMetadata("adapt-mutation-exposed", Adapt.instance);
    for (UUID viewerId : exposedViewers) {
      clearExposureGlow(player, viewerId);
    }
  }

  void clearGlobalState() {
    store.spawnProvenance.clear();
    store.deathMutationGrants.clear();
    store.quarryOwners.clear();
    synchronized (farmPruneLock) {
      farmDeaths.clear();
      farmPruneIterator = null;
    }
  }

  private void queueProjectileMutationHit(
      UUID attackerId,
      Location projectileOrigin,
      LivingEntity target,
      UUID targetId,
      UUID targetWorldId,
      Location targetLocation,
      boolean playerTarget,
      MutationEventClaims claims
  ) {
    Player attacker = access.onlinePlayer(attackerId);
    if (attacker == null) {
      return;
    }
    ProjectileHitStage hit = new ProjectileHitStage(
        targetId,
        targetWorldId,
        targetLocation.clone(),
        projectileOrigin.clone(),
        playerTarget
    );
    J.runEntity(attacker, () -> {
      if (!attacker.isOnline() || !access.protection().canAffectAt(attacker, hit.targetLocation(), playerTarget)) {
        return;
      }
      for (MutationType type : access.ordered(attacker)) {
        if (playerTarget && !access.pvpEnabled(type)) {
          continue;
        }
        switch (type) {
          case PACKMIND -> handlePackProjectileOwnerHit(attacker, hit);
          case TROPHY_CRUCIBLE -> prepareProjectileTrophy(attacker, target, hit, claims);
          case ARSENAL_CORTEX -> handleProjectileArsenal(attacker, target, hit, claims);
          case UMBRAL_ECHO -> handleProjectileUmbral(
              attacker,
              target,
              hit,
              claims
          );
          default -> {
          }
        }
      }
    });
  }

  private void handlePackProjectileOwnerHit(Player owner, ProjectileHitStage hit) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(owner.getUniqueId());
    MutationConfig.Packmind config = access.config().getPackmind();
    long now = System.currentTimeMillis();
    UUID scheduledQuarryId = null;
    long scheduledGeneration = 0L;
    long scheduledExpiry = 0L;
    synchronized (runtime) {
      if (runtime.pack.quarryId == null || runtime.pack.expiresAt <= now || !runtime.pack.quarryId.equals(hit.targetId())) {
        clearPackOwned(owner.getUniqueId(), runtime);
        if (!registerQuarryOwner(hit.targetId(), owner.getUniqueId())) {
          return;
        }
        runtime.pack.quarryId = hit.targetId();
        runtime.pack.quarryWorldId = hit.targetWorldId();
        runtime.pack.expiresAt = now + config.getQuarryMillis();
        scheduledQuarryId = hit.targetId();
        scheduledGeneration = runtime.pack.generation;
        scheduledExpiry = runtime.pack.expiresAt;
      }
      prunePackMembers(runtime.pack, now);
      if (runtime.pack.members.isEmpty()) {
        runtime.pack.tempo = 0;
      }
    }
    if (scheduledQuarryId != null) {
      schedulePackExpiry(owner, scheduledQuarryId, scheduledGeneration, scheduledExpiry);
    }
  }

  private void applyPackProjectileBurden(
      EntityDamageByEntityEvent event,
      UUID ownerId,
      boolean playerTarget
  ) {
    if (playerTarget && !access.pvpEnabled(MutationType.PACKMIND)) {
      return;
    }
    MutationManager manager = access.manager();
    MutationSnapshot snapshot = manager == null
        ? MutationSnapshot.empty()
        : manager.snapshot(ownerId);
    if (!snapshot.expressed().contains(MutationType.PACKMIND) || snapshot.perfect()) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    boolean hasOther = false;
    if (runtime != null) {
      synchronized (runtime) {
        prunePackMembers(runtime.pack, System.currentTimeMillis());
        hasOther = !runtime.pack.members.isEmpty();
        if (!hasOther) {
          runtime.pack.tempo = 0;
        }
      }
    }
    if (!hasOther) {
      event.setDamage(event.getDamage() * access.config().getPackmind().getWaitingDamageFactor());
    }
  }

  private void prepareProjectileTrophy(
      Player attacker,
      LivingEntity target,
      ProjectileHitStage hit,
      MutationEventClaims claims
  ) {
    PlayerMutationData durable = access.durable(attacker);
    if (durable == null || durable.getTrophyImprint().isBlank()
        || clearExpiredTrophy(attacker, durable, System.currentTimeMillis())) {
      return;
    }
    String family = durable.getTrophyImprint();
    long expiry = durable.getTrophyImprintExpiresAt();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(attacker.getUniqueId());
    long reservationGeneration;
    long loadoutGeneration;
    synchronized (runtime) {
      reservationGeneration = runtime.trophy.reserve(family, expiry);
      loadoutGeneration = runtime.loadoutGeneration;
    }
    if (reservationGeneration == 0L
        || claims == null
        || !claims.tryClaim(MutationClaim.UTILITY_ECHO)
        || !runtime.tryClaimUtilityEcho(System.currentTimeMillis())) {
      releaseTrophyReservation(attacker.getUniqueId(), reservationGeneration);
      return;
    }
    ProjectileUtilityRequest request = new ProjectileUtilityRequest(
        attacker.getUniqueId(),
        hit,
        utilityForFamily(family),
        1D,
        MutationType.TROPHY_CRUCIBLE,
        loadoutGeneration,
        0L,
        reservationGeneration,
        family,
        expiry,
        0
    );
    if (!queueProjectileUtility(target, request)) {
      releaseTrophyReservation(attacker.getUniqueId(), reservationGeneration);
    }
  }

  private void handleProjectileArsenal(
      Player attacker,
      LivingEntity target,
      ProjectileHitStage hit,
      MutationEventClaims claims
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(attacker.getUniqueId());
    MutationConfig.ArsenalCortex config = access.config().getArsenalCortex();
    MutationUtilityTag inherited;
    long loadoutGeneration;
    long now = System.currentTimeMillis();
    synchronized (runtime) {
      if (runtime.arsenal.expiresAt <= now) {
        runtime.arsenal.clear();
      }
      if (runtime.arsenal.dullUntil > now) {
        return;
      }
      if (runtime.arsenal.family == MutationWeaponFamily.RANGED) {
        if (!access.perfect(attacker)) {
          runtime.arsenal.dullUntil = now + config.getDullnessMillis();
          access.tell(attacker, MutationType.ARSENAL_CORTEX, Particle.SMOKE, 5);
        }
        return;
      }
      inherited = runtime.arsenal.tag;
      runtime.arsenal.family = MutationWeaponFamily.RANGED;
      runtime.arsenal.tag = utilityFor(MutationWeaponFamily.RANGED);
      runtime.arsenal.chainLength = Math.min(config.getMaximumChain(), runtime.arsenal.chainLength + 1);
      runtime.arsenal.expiresAt = now + config.getChainTimeoutMillis();
      loadoutGeneration = runtime.loadoutGeneration;
    }
    if (inherited != MutationUtilityTag.NONE
        && claims != null
        && claims.tryClaim(MutationClaim.UTILITY_ECHO)
        && runtime.tryClaimUtilityEcho(now)) {
      queueProjectileUtility(target, new ProjectileUtilityRequest(
          attacker.getUniqueId(),
          hit,
          inherited,
          0.6D,
          MutationType.ARSENAL_CORTEX,
          loadoutGeneration,
          0L,
          0L,
          "",
          0L,
          0
      ));
    }
    access.tell(attacker, MutationType.ARSENAL_CORTEX, Particle.ENCHANT, 4);
  }

  private void handleProjectileUmbral(
      Player attacker,
      LivingEntity target,
      ProjectileHitStage hit,
      MutationEventClaims claims
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(attacker.getUniqueId());
    MutationConfig.UmbralEcho config = access.config().getUmbralEcho();
    int angle = attackAngleBucket(hit.projectileOrigin(), hit.targetLocation(), config.getAngleBucketDegrees());
    long now = System.currentTimeMillis();
    boolean novel;
    long generation;
    long loadoutGeneration;
    synchronized (runtime) {
      runtime.umbral.memories.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
      MutationRuntimeStore.UmbralMemory prior = runtime.umbral.memories.get(hit.targetId());
      novel = prior == null || prior.angleBucket() != angle || prior.family() != MutationWeaponFamily.RANGED;
      runtime.umbral.memories.put(hit.targetId(), new MutationRuntimeStore.UmbralMemory(
          angle,
          MutationWeaponFamily.RANGED,
          now + config.getTechniqueMemoryMillis()
      ));
      while (runtime.umbral.memories.size() > config.getMaximumTargetMemories()) {
        runtime.umbral.memories.remove(runtime.umbral.memories.keySet().iterator().next());
      }
      generation = runtime.umbral.generation;
      loadoutGeneration = runtime.loadoutGeneration;
    }
    if (!novel) {
      if (!access.perfect(attacker)) {
        expose(attacker, target, hit.targetId(), config.getExposureTicks());
      }
      return;
    }
    if (claims == null || !claims.tryClaim(MutationClaim.UTILITY_ECHO) || !runtime.tryClaimUtilityEcho(now)) {
      return;
    }
    queueProjectileUtility(target, new ProjectileUtilityRequest(
        attacker.getUniqueId(),
        hit,
        MutationUtilityTag.PINNING,
        0.6D,
        MutationType.UMBRAL_ECHO,
        loadoutGeneration,
        generation,
        0L,
        "",
        0L,
        config.getEchoDelayTicks()
    ));
  }

  private boolean queueProjectileUtility(
      LivingEntity target,
      ProjectileUtilityRequest request
  ) {
    if (request.tag() == null || request.tag() == MutationUtilityTag.NONE) {
      return false;
    }
    Runnable recheck = () -> recheckProjectileTarget(target, request);
    if (request.delayTicks() > 0) {
      return J.runEntity(target, recheck, request.delayTicks());
    }
    return J.runEntity(target, recheck);
  }

  private void recheckProjectileTarget(LivingEntity target, ProjectileUtilityRequest request) {
    if (!validProjectileTarget(target, request.hit())
        || (request.hit().playerTarget() && !access.pvpEnabled(request.sourceType()))) {
      releaseTrophyReservation(request.actorId(), request.reservationGeneration());
      return;
    }
    Location targetLocation = target.getLocation().clone();
    Player actor = access.onlinePlayer(request.actorId());
    if (actor == null || !J.runEntity(actor, () -> authorizeProjectileUtility(actor, target, targetLocation, request))) {
      releaseTrophyReservation(request.actorId(), request.reservationGeneration());
    }
  }

  private void authorizeProjectileUtility(
      Player actor,
      LivingEntity target,
      Location targetLocation,
      ProjectileUtilityRequest request
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.actorId());
    if (!validProjectileSource(actor, runtime, request)
        || !access.protection().canAffectAt(actor, targetLocation, request.hit().playerTarget())) {
      releaseTrophyReservation(request.actorId(), request.reservationGeneration());
      return;
    }
    if (request.sourceType() == MutationType.TROPHY_CRUCIBLE
        && !commitProjectileTrophy(actor, runtime, request)) {
      releaseTrophyReservation(request.actorId(), request.reservationGeneration());
      return;
    }
    ProjectileUtilityAuthorization authorization = new ProjectileUtilityAuthorization(
        request,
        targetLocation.clone(),
        System.currentTimeMillis()
    );
    if (!J.runEntity(target, () -> applyAuthorizedProjectileUtility(actor, target, authorization))) {
      releaseTrophyReservation(request.actorId(), request.reservationGeneration());
    }
  }

  private boolean validProjectileSource(
      Player actor,
      MutationRuntimeStore.PlayerRuntimeState runtime,
      ProjectileUtilityRequest request
  ) {
    if (!validProjectileCapability(runtime, request)
        || !actor.isOnline()
        || !access.expressed(actor, request.sourceType())
        || (request.hit().playerTarget() && !access.pvpEnabled(request.sourceType()))) {
      return false;
    }
    if (request.sourceType() != MutationType.TROPHY_CRUCIBLE) {
      return true;
    }
    PlayerMutationData durable = access.durable(actor);
    return durable != null && activeTrophyReservation(
        durable.getTrophyImprint(),
        durable.getTrophyImprintExpiresAt(),
        request.reservedFamily(),
        request.reservedExpiry(),
        System.currentTimeMillis()
    );
  }

  private boolean validProjectileCapability(
      MutationRuntimeStore.PlayerRuntimeState runtime,
      ProjectileUtilityRequest request
  ) {
    if (runtime == null) {
      return false;
    }
    synchronized (runtime) {
      if (runtime.loadoutGeneration != request.loadoutGeneration()) {
        return false;
      }
      if (request.sourceType() == MutationType.UMBRAL_ECHO) {
        MutationRuntimeStore.UmbralMemory memory = runtime.umbral.memories.get(request.hit().targetId());
        if (runtime.umbral.generation != request.sourceGeneration()
            || memory == null
            || memory.expiresAt() <= System.currentTimeMillis()) {
          return false;
        }
      }
      if (request.sourceType() == MutationType.TROPHY_CRUCIBLE
          && !runtime.trophy.matches(
          request.reservationGeneration(),
          request.reservedFamily(),
          request.reservedExpiry()
      )) {
        return false;
      }
    }
    return true;
  }

  private void applyAuthorizedProjectileUtility(
      Player actor,
      LivingEntity target,
      ProjectileUtilityAuthorization authorization
  ) {
    ProjectileUtilityRequest request = authorization.request();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.actorId());
    if (!validProjectileCapability(runtime, request)
        || (request.sourceType() == MutationType.TROPHY_CRUCIBLE
        && !validCommittedTrophyCapability(runtime, request))
        || !validProjectileTarget(target, request.hit())
        || !sameBlock(target.getLocation(), authorization.targetLocation())
        || System.currentTimeMillis() - authorization.authorizedAt() > PROTECTION_AUTHORIZATION_MILLIS
        || (request.hit().playerTarget() && !access.pvpEnabled(request.sourceType()))) {
      releaseTrophyReservation(request.actorId(), request.reservationGeneration());
      return;
    }
    applyUtilityOwned(
        request.hit().projectileOrigin(),
        target,
        request.tag(),
        MutationRuntimePolicy.clamp(request.factor(), 0.1D, 1D)
    );
    if (request.sourceType() == MutationType.TROPHY_CRUCIBLE) {
      releaseTrophyReservation(request.actorId(), request.reservationGeneration());
      J.runEntity(actor, () -> access.tell(actor, MutationType.TROPHY_CRUCIBLE, Particle.ENCHANT, 8));
    } else if (request.sourceType() == MutationType.UMBRAL_ECHO) {
      J.runEntity(actor, () -> access.tell(actor, MutationType.UMBRAL_ECHO, Particle.REVERSE_PORTAL, 8));
    }
  }

  private boolean commitProjectileTrophy(
      Player actor,
      MutationRuntimeStore.PlayerRuntimeState runtime,
      ProjectileUtilityRequest request
  ) {
    PlayerMutationData durable = access.durable(actor);
    if (durable == null || !activeTrophyReservation(
        durable.getTrophyImprint(),
        durable.getTrophyImprintExpiresAt(),
        request.reservedFamily(),
        request.reservedExpiry(),
        System.currentTimeMillis()
    )) {
      return false;
    }
    synchronized (runtime) {
      if (!runtime.trophy.commit(
          request.reservationGeneration(),
          request.reservedFamily(),
          request.reservedExpiry()
      )) {
        return false;
      }
    }
    durable.setTrophyImprint("");
    durable.setTrophyImprintExpiresAt(0L);
    access.save(actor);
    return true;
  }

  private boolean validCommittedTrophyCapability(
      MutationRuntimeStore.PlayerRuntimeState runtime,
      ProjectileUtilityRequest request
  ) {
    if (runtime == null) {
      return false;
    }
    synchronized (runtime) {
      return runtime.trophy.committed(
          request.reservationGeneration(),
          request.reservedFamily(),
          request.reservedExpiry()
      );
    }
  }

  private void releaseTrophyReservation(UUID actorId, long generation) {
    if (generation == 0L) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(actorId);
    if (runtime != null) {
      synchronized (runtime) {
        runtime.trophy.release(generation);
      }
    }
  }

  private boolean validProjectileTarget(LivingEntity target, ProjectileHitStage hit) {
    return target != null
        && target.isValid()
        && !target.isDead()
        && target.getUniqueId().equals(hit.targetId())
        && target.getWorld().getUID().equals(hit.targetWorldId());
  }

  private void handleArsenal(Player attacker, LivingEntity target, Entity damager, MutationEventClaims claims) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(attacker.getUniqueId());
    MutationConfig.ArsenalCortex config = access.config().getArsenalCortex();
    MutationWeaponFamily family = weaponFamily(attacker, damager);
    MutationUtilityTag inherited;
    long now = System.currentTimeMillis();
    synchronized (runtime) {
      if (runtime.arsenal.expiresAt <= now) {
        runtime.arsenal.clear();
      }
      if (runtime.arsenal.dullUntil > now) {
        return;
      }
      if (runtime.arsenal.family == family) {
        if (!access.perfect(attacker)) {
          runtime.arsenal.dullUntil = now + config.getDullnessMillis();
          access.tell(attacker, MutationType.ARSENAL_CORTEX, Particle.SMOKE, 5);
        }
        return;
      }
      inherited = runtime.arsenal.tag;
      runtime.arsenal.family = family;
      runtime.arsenal.tag = utilityFor(family);
      runtime.arsenal.chainLength = Math.min(config.getMaximumChain(), runtime.arsenal.chainLength + 1);
      runtime.arsenal.expiresAt = now + config.getChainTimeoutMillis();
    }
    if (inherited != MutationUtilityTag.NONE) {
      applyUtility(attacker, target, inherited, 0.6D, claims, true);
    }
    access.tell(attacker, MutationType.ARSENAL_CORTEX, Particle.ENCHANT, 4);
  }

  private void handlePackOwnerHit(EntityDamageByEntityEvent event, Player owner, LivingEntity target) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(owner.getUniqueId());
    MutationConfig.Packmind config = access.config().getPackmind();
    long now = System.currentTimeMillis();
    UUID scheduledQuarryId = null;
    long scheduledGeneration = 0L;
    long scheduledExpiry = 0L;
    synchronized (runtime) {
      if (runtime.pack.quarryId == null || runtime.pack.expiresAt <= now || !runtime.pack.quarryId.equals(target.getUniqueId())) {
        clearPackOwned(owner.getUniqueId(), runtime);
        if (!registerQuarryOwner(target.getUniqueId(), owner.getUniqueId())) {
          return;
        }
        runtime.pack.quarryId = target.getUniqueId();
        runtime.pack.quarryWorldId = target.getWorld().getUID();
        runtime.pack.expiresAt = now + config.getQuarryMillis();
        scheduledQuarryId = runtime.pack.quarryId;
        scheduledGeneration = runtime.pack.generation;
        scheduledExpiry = runtime.pack.expiresAt;
      }
      prunePackMembers(runtime.pack, now);
      boolean hasOther = false;
      for (UUID memberId : runtime.pack.members.keySet()) {
        if (!memberId.equals(owner.getUniqueId())) {
          hasOther = true;
          break;
        }
      }
      if (!hasOther) {
        runtime.pack.tempo = 0;
      }
      if (!hasOther && !access.perfect(owner)) {
        event.setDamage(event.getDamage() * config.getWaitingDamageFactor());
      }
    }
    if (scheduledQuarryId != null) {
      schedulePackExpiry(owner, scheduledQuarryId, scheduledGeneration, scheduledExpiry);
    }
  }

  private void applyPackContributions(EntityDamageByEntityEvent event, Player directPlayer, LivingEntity target) {
    Set<UUID> ownerIds = store.quarryOwners.get(target.getUniqueId());
    if (ownerIds == null || ownerIds.isEmpty()) {
      return;
    }
    UUID contributorId = resolver.packContributorId(event.getDamager());
    if (contributorId == null) {
      return;
    }
    Location contributorLocation = event.getDamager().getLocation().clone();
    Location targetLocation = target.getLocation().clone();
    UUID targetId = target.getUniqueId();
    UUID targetWorldId = target.getWorld().getUID();
    boolean playerTarget = target instanceof Player;
    for (UUID ownerId : List.copyOf(ownerIds)) {
      Player owner = access.onlinePlayer(ownerId);
      if (owner == null || ownerId.equals(contributorId)) {
        if (owner == null) {
          clearPack(ownerId);
          ownerIds.remove(ownerId);
          if (ownerIds.isEmpty()) {
            store.quarryOwners.remove(targetId, ownerIds);
          }
        }
        continue;
      }
      if (target instanceof Player && !access.pvpEnabled(MutationType.PACKMIND)) {
        continue;
      }
      if (resolver.packOwnedBy(event.getDamager(), ownerId)) {
        J.runEntity(owner, () -> recordPackContribution(
            owner,
            contributorId,
            contributorLocation,
            target,
            targetId,
            targetWorldId,
            targetLocation,
            playerTarget
        ));
      } else if (directPlayer != null) {
        queuePlayerPackContribution(
            owner,
            directPlayer,
            contributorId,
            target,
            targetId,
            targetWorldId,
            targetLocation,
            playerTarget
        );
      }
    }
  }

  private void queuePlayerPackContribution(
      Player owner,
      Player contributor,
      UUID contributorId,
      LivingEntity target,
      UUID targetId,
      UUID targetWorldId,
      Location targetLocation,
      boolean playerTarget
  ) {
    J.runEntity(owner, () -> {
      if (!validPackOwner(owner, targetId, targetWorldId, targetLocation, playerTarget)) {
        return;
      }
      MutationRuntimeAccess.CooperativeConsent consent = access.cooperativeConsent(owner);
      J.runEntity(contributor, () -> {
        if (!access.consented(consent, contributor) || contributor.getWorld() != targetLocation.getWorld()) {
          return;
        }
        double range = access.config().getPackmind().getParticipationRange();
        if (contributor.getLocation().distanceSquared(targetLocation) > range * range) {
          return;
        }
        Location contributorLocation = contributor.getLocation().clone();
        J.runEntity(owner, () -> recordPackContribution(
            owner,
            contributorId,
            contributorLocation,
            target,
            targetId,
            targetWorldId,
            targetLocation,
            playerTarget
        ));
      });
    });
  }

  private void recordPackContribution(
      Player owner,
      UUID contributorId,
      Location contributorLocation,
      LivingEntity target,
      UUID targetId,
      UUID targetWorldId,
      Location targetLocation,
      boolean playerTarget
  ) {
    if (!validPackOwner(owner, targetId, targetWorldId, targetLocation, playerTarget)
        || contributorLocation.getWorld() != targetLocation.getWorld()
        || !access.protection().canAffectAt(owner, targetLocation, playerTarget)) {
      return;
    }
    double range = access.config().getPackmind().getParticipationRange();
    if (contributorLocation.distanceSquared(targetLocation) > range * range) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(owner.getUniqueId());
    boolean tempoSurge;
    long generation;
    long now = System.currentTimeMillis();
    synchronized (runtime) {
      prunePackMembers(runtime.pack, now);
      int maximumMembers = access.config().getPackmind().getMaximumMembers();
      if (runtime.pack.members.size() >= maximumMembers && !runtime.pack.members.containsKey(contributorId)) {
        return;
      }
      runtime.pack.members.put(contributorId, now);
      int maximumTempo = access.config().getPackmind().getMaximumTempo();
      runtime.pack.tempo = Math.min(maximumTempo, runtime.pack.tempo + 1);
      tempoSurge = runtime.pack.tempo >= maximumTempo;
      if (tempoSurge) {
        runtime.pack.tempo = 0;
      }
      generation = runtime.pack.generation;
    }
    J.runEntity(target, () -> recheckPackTarget(
        owner.getUniqueId(),
        target,
        targetId,
        targetWorldId,
        generation,
        tempoSurge,
        playerTarget
    ));
    access.tell(owner, MutationType.PACKMIND, Particle.WAX_ON, 4);
  }

  private boolean validPackOwner(
      Player owner,
      UUID targetId,
      UUID targetWorldId,
      Location targetLocation,
      boolean playerTarget
  ) {
    double range = access.config().getPackmind().getParticipationRange();
    if (!owner.isOnline() || !access.expressed(owner, MutationType.PACKMIND)
        || !owner.getWorld().getUID().equals(targetWorldId)
        || owner.getLocation().distanceSquared(targetLocation) > range * range
        || (playerTarget && !access.pvpEnabled(MutationType.PACKMIND))) {
      clearPack(owner.getUniqueId());
      return false;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(owner.getUniqueId());
    synchronized (runtime) {
      if (!targetId.equals(runtime.pack.quarryId) || !targetWorldId.equals(runtime.pack.quarryWorldId)
          || runtime.pack.expiresAt <= System.currentTimeMillis()) {
        clearPackOwned(owner.getUniqueId(), runtime);
        return false;
      }
    }
    return true;
  }

  private void recheckPackTarget(
      UUID ownerId,
      LivingEntity target,
      UUID targetId,
      UUID targetWorldId,
      long generation,
      boolean tempoSurge,
      boolean playerTarget
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (runtime.pack.generation != generation || !target.getUniqueId().equals(runtime.pack.quarryId)
          || runtime.pack.expiresAt <= System.currentTimeMillis()) {
        return;
      }
    }
    if (!target.isValid() || target.isDead()
        || !target.getUniqueId().equals(targetId)
        || !target.getWorld().getUID().equals(targetWorldId)
        || (playerTarget && !access.pvpEnabled(MutationType.PACKMIND))) {
      return;
    }
    Location targetLocation = target.getLocation().clone();
    Player owner = access.onlinePlayer(ownerId);
    if (owner == null) {
      return;
    }
    J.runEntity(owner, () -> authorizePackControl(
        owner,
        target,
        targetId,
        targetWorldId,
        targetLocation,
        generation,
        tempoSurge,
        playerTarget
    ));
  }

  private void authorizePackControl(
      Player owner,
      LivingEntity target,
      UUID targetId,
      UUID targetWorldId,
      Location targetLocation,
      long generation,
      boolean tempoSurge,
      boolean playerTarget
  ) {
    if (!validPackOwner(owner, targetId, targetWorldId, targetLocation, playerTarget)
        || !access.protection().canAffectAt(owner, targetLocation, playerTarget)) {
      return;
    }
    J.runEntity(target, () -> applyPackControl(
        owner.getUniqueId(),
        target,
        targetId,
        targetWorldId,
        targetLocation,
        generation,
        tempoSurge,
        playerTarget
    ));
  }

  private void applyPackControl(
      UUID ownerId,
      LivingEntity target,
      UUID targetId,
      UUID targetWorldId,
      Location authorizedLocation,
      long generation,
      boolean tempoSurge,
      boolean playerTarget
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (runtime.pack.generation != generation || !targetId.equals(runtime.pack.quarryId)
          || runtime.pack.expiresAt <= System.currentTimeMillis()) {
        return;
      }
    }
    if (!target.isValid() || target.isDead()
        || !target.getUniqueId().equals(targetId)
        || !target.getWorld().getUID().equals(targetWorldId)
        || !sameBlock(target.getLocation(), authorizedLocation)
        || (playerTarget && !access.pvpEnabled(MutationType.PACKMIND))) {
      return;
    }
    int duration = tempoSurge ? 30 : 10;
    int amplifier = tempoSurge ? 1 : 0;
    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier, true, false, true));
  }

  private boolean registerQuarryOwner(UUID quarryId, UUID ownerId) {
    Set<UUID> owners = store.quarryOwners.computeIfAbsent(quarryId, ignored -> ConcurrentHashMap.newKeySet());
    synchronized (owners) {
      if (owners.size() >= MAX_QUARRY_OWNERS && !owners.contains(ownerId)) {
        return false;
      }
      owners.add(ownerId);
      return true;
    }
  }

  private void schedulePackExpiry(Player owner, UUID quarryId, long generation, long expiresAt) {
    long remaining = Math.max(1L, expiresAt - System.currentTimeMillis());
    int delayTicks = Math.max(1, (int) Math.min(Integer.MAX_VALUE, (remaining + 49L) / 50L));
    J.runEntity(owner, () -> expirePack(owner, quarryId, generation), delayTicks);
  }

  private void expirePack(Player owner, UUID quarryId, long generation) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(owner.getUniqueId());
    if (runtime == null) {
      return;
    }
    long expiresAt;
    synchronized (runtime) {
      if (runtime.pack.generation != generation || !quarryId.equals(runtime.pack.quarryId)) {
        return;
      }
      expiresAt = runtime.pack.expiresAt;
      if (expiresAt <= System.currentTimeMillis()) {
        clearPackOwned(owner.getUniqueId(), runtime);
        return;
      }
    }
    schedulePackExpiry(owner, quarryId, generation, expiresAt);
  }

  private void prunePackMembers(MutationRuntimeStore.PackState pack, long now) {
    pack.members.entrySet().removeIf(entry -> entry.getValue() < now - PACK_CONTRIBUTION_MILLIS);
  }

  private void clearTrophyAtStation(
      PlayerInteractEvent event,
      Player player,
      PlayerMutationData durable,
      long now
  ) {
    if (durable.getTrophyImprint().isBlank()) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    boolean confirmed;
    synchronized (runtime) {
      confirmed = runtime.trophyClearConfirmUntil >= now;
      runtime.trophyClearConfirmUntil = confirmed ? 0L : now + TROPHY_CLEAR_CONFIRMATION_MILLIS;
    }
    event.setCancelled(true);
    if (!confirmed) {
      player.sendMessage(ChatColor.YELLOW + AdaptLanguage.text(MutationMessages.TROPHY_CLEAR_HINT));
      return;
    }
    durable.setTrophyImprint("");
    durable.setTrophyImprintExpiresAt(0L);
    synchronized (runtime) {
      runtime.trophy.clear();
    }
    access.save(player);
    access.tell(player, MutationType.TROPHY_CRUCIBLE, Particle.SMOKE, 8);
    player.sendMessage(ChatColor.GRAY + AdaptLanguage.text(MutationMessages.TROPHY_CLEARED));
  }

  private boolean clearExpiredTrophy(Player player, PlayerMutationData durable, long now) {
    if (durable.getTrophyImprint().isBlank() || durable.getTrophyImprintExpiresAt() > now) {
      return false;
    }
    durable.setTrophyImprint("");
    durable.setTrophyImprintExpiresAt(0L);
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime != null) {
      synchronized (runtime) {
        runtime.trophyClearConfirmUntil = 0L;
        runtime.trophy.clear();
      }
    }
    access.save(player);
    return true;
  }

  private void scheduleTrophyExpiry(Player player, String family, long expiresAt) {
    long remaining = Math.max(1L, expiresAt - System.currentTimeMillis());
    int delayTicks = Math.max(1, (int) Math.min(Integer.MAX_VALUE, (remaining + 49L) / 50L));
    J.runEntity(player, () -> expireTrophy(player, family, expiresAt), delayTicks);
  }

  private void expireTrophy(Player player, String family, long expectedExpiry) {
    PlayerMutationData durable = access.durable(player);
    if (durable == null || !family.equals(durable.getTrophyImprint())
        || durable.getTrophyImprintExpiresAt() != expectedExpiry) {
      return;
    }
    if (expectedExpiry > System.currentTimeMillis()) {
      scheduleTrophyExpiry(player, family, expectedExpiry);
      return;
    }
    clearExpiredTrophy(player, durable, System.currentTimeMillis());
  }

  private void spendTrophy(Player attacker, LivingEntity target, MutationEventClaims claims) {
    PlayerMutationData durable = access.durable(attacker);
    if (durable == null || durable.getTrophyImprint().isBlank()) {
      return;
    }
    if (clearExpiredTrophy(attacker, durable, System.currentTimeMillis())) {
      return;
    }
    MutationUtilityTag tag = utilityForFamily(durable.getTrophyImprint());
    if (!applyUtility(attacker, target, tag, 1D, claims, true)) {
      return;
    }
    durable.setTrophyImprint("");
    durable.setTrophyImprintExpiresAt(0L);
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(attacker.getUniqueId());
    if (runtime != null) {
      synchronized (runtime) {
        runtime.trophy.clear();
      }
    }
    access.save(attacker);
    access.tell(attacker, MutationType.TROPHY_CRUCIBLE, Particle.ENCHANT, 8);
  }

  private void handleUmbral(Player attacker, LivingEntity target, Entity damager, MutationEventClaims claims) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(attacker.getUniqueId());
    MutationConfig.UmbralEcho config = access.config().getUmbralEcho();
    MutationWeaponFamily family = weaponFamily(attacker, damager);
    int angle = attackAngleBucket(attacker, target, config.getAngleBucketDegrees());
    long now = System.currentTimeMillis();
    boolean novel;
    long generation;
    long loadoutGeneration;
    synchronized (runtime) {
      runtime.umbral.memories.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
      MutationRuntimeStore.UmbralMemory prior = runtime.umbral.memories.get(target.getUniqueId());
      novel = prior == null || prior.angleBucket() != angle || prior.family() != family;
      runtime.umbral.memories.put(target.getUniqueId(), new MutationRuntimeStore.UmbralMemory(
          angle,
          family,
          now + config.getTechniqueMemoryMillis()
      ));
      while (runtime.umbral.memories.size() > config.getMaximumTargetMemories()) {
        UUID oldest = runtime.umbral.memories.keySet().iterator().next();
        runtime.umbral.memories.remove(oldest);
      }
      generation = runtime.umbral.generation;
      loadoutGeneration = runtime.loadoutGeneration;
    }
    if (!novel) {
      if (!access.perfect(attacker)) {
        expose(attacker, target, target.getUniqueId(), config.getExposureTicks());
      }
      return;
    }
    if (!claims.tryClaim(MutationClaim.UTILITY_ECHO)) {
      return;
    }
    if (!runtime.tryClaimUtilityEcho(now)) {
      return;
    }
    MutationUtilityTag tag = utilityFor(family);
    Location actorOrigin = damager.getLocation().clone();
    ProjectileHitStage hit = new ProjectileHitStage(
        target.getUniqueId(),
        target.getWorld().getUID(),
        target.getLocation().clone(),
        actorOrigin,
        target instanceof Player
    );
    queueProjectileUtility(target, new ProjectileUtilityRequest(
        attacker.getUniqueId(),
        hit,
        tag,
        0.6D,
        MutationType.UMBRAL_ECHO,
        loadoutGeneration,
        generation,
        0L,
        "",
        0L,
        config.getEchoDelayTicks()
    ));
  }

  private void expose(Player attacker, LivingEntity target, UUID targetId, int ticks) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(attacker.getUniqueId());
    long generation;
    synchronized (runtime) {
      runtime.umbral.exposureGeneration++;
      generation = runtime.umbral.exposureGeneration;
    }
    J.runEntity(attacker, () -> {
      MutationRuntimeStore.PlayerRuntimeState current = store.existing(attacker.getUniqueId());
      if (current == null) {
        return;
      }
      synchronized (current) {
        if (current.umbral.exposureGeneration != generation) {
          return;
        }
      }
      attacker.setMetadata("adapt-mutation-exposed", new FixedMetadataValue(Adapt.instance, true));
      access.tell(attacker, MutationType.UMBRAL_ECHO, Particle.SMOKE, 6);
    });
    if (target instanceof Player viewer) {
      ViewerGlowCoordinator glowCoordinator = Adapt.instance.getViewerGlowCoordinator();
      if (glowCoordinator != null && glowCoordinator.isAvailable()) {
        UUID viewerId = targetId;
        UUID attackerId = attacker.getUniqueId();
        int attackerRuntimeEntityId = attacker.getEntityId();
        ArrayList<UUID> evictedViewerIds = new ArrayList<>();
        synchronized (runtime) {
          runtime.umbral.exposedViewers.put(viewerId, generation);
          while (runtime.umbral.exposedViewers.size() > access.config().getUmbralEcho().getMaximumTargetMemories()) {
            UUID oldest = runtime.umbral.exposedViewers.keySet().iterator().next();
            runtime.umbral.exposedViewers.remove(oldest);
            evictedViewerIds.add(oldest);
          }
        }
        for (UUID evictedViewerId : evictedViewerIds) {
          clearExposureGlow(attacker, evictedViewerId);
        }
        J.runEntity(viewer, () -> {
          MutationRuntimeStore.PlayerRuntimeState current = store.existing(attacker.getUniqueId());
          if (current == null) {
            return;
          }
          synchronized (current) {
            if (!Long.valueOf(generation).equals(current.umbral.exposedViewers.get(viewerId))) {
              return;
            }
          }
          glowCoordinator.set(
              ViewerGlowCoordinator.Layer.MUTATION_UMBRAL_ECHO,
              attacker,
              viewer,
              ChatColor.LIGHT_PURPLE
          );
        });
        J.runEntity(viewer, () -> {
          MutationRuntimeStore.PlayerRuntimeState current = store.existing(attacker.getUniqueId());
          if (current != null) {
            synchronized (current) {
              Long trackedGeneration = current.umbral.exposedViewers.get(viewerId);
              if (trackedGeneration != null && trackedGeneration != generation) {
                return;
              }
              current.umbral.exposedViewers.remove(viewerId);
            }
          }
          glowCoordinator.unset(
              ViewerGlowCoordinator.Layer.MUTATION_UMBRAL_ECHO,
              attackerId,
              attackerRuntimeEntityId,
              viewer
          );
        }, ticks);
      }
    }
    J.runEntity(attacker, () -> {
      MutationRuntimeStore.PlayerRuntimeState current = store.existing(attacker.getUniqueId());
      if (current == null) {
        return;
      }
      synchronized (current) {
        if (current.umbral.exposureGeneration != generation) {
          return;
        }
      }
      attacker.removeMetadata("adapt-mutation-exposed", Adapt.instance);
    }, ticks);
  }

  private void clearExposureGlow(Player attacker, UUID viewerId) {
    Player viewer = access.onlinePlayer(viewerId);
    ViewerGlowCoordinator glowCoordinator = Adapt.instance.getViewerGlowCoordinator();
    if (viewer == null || glowCoordinator == null) {
      return;
    }
    UUID attackerId = attacker.getUniqueId();
    int attackerRuntimeEntityId = attacker.getEntityId();
    J.runEntity(viewer, () -> glowCoordinator.unset(
        ViewerGlowCoordinator.Layer.MUTATION_UMBRAL_ECHO,
        attackerId,
        attackerRuntimeEntityId,
        viewer
    ));
  }

  private void clearQuarry(UUID quarryId) {
    Set<UUID> owners = store.quarryOwners.remove(quarryId);
    if (owners == null) {
      return;
    }
    for (UUID ownerId : owners) {
      MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
      if (runtime == null) {
        continue;
      }
      synchronized (runtime) {
        if (quarryId.equals(runtime.pack.quarryId)) {
          runtime.pack.clear();
        }
      }
    }
  }

  private void clearPack(UUID ownerId) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      clearPackOwned(ownerId, runtime);
    }
  }

  private void clearPackOwned(UUID ownerId, MutationRuntimeStore.PlayerRuntimeState runtime) {
    UUID quarryId = runtime.pack.quarryId;
    runtime.pack.clear();
    if (quarryId == null) {
      return;
    }
    Set<UUID> owners = store.quarryOwners.get(quarryId);
    if (owners != null) {
      synchronized (owners) {
        owners.remove(ownerId);
        if (owners.isEmpty()) {
          store.quarryOwners.remove(quarryId, owners);
        }
      }
    }
  }

  private void authorizeProjectileDeathGrant(Player attacker, ProjectileDeathGrantRequest request) {
    if (!attacker.isOnline()
        || !access.protection().canAffectAt(attacker, request.targetLocation(), request.playerTarget())) {
      return;
    }
    MutationSnapshot snapshot = access.snapshot(attacker);
    if (request.playerTarget()) {
      boolean trophyPvp = snapshot.expressed().contains(MutationType.TROPHY_CRUCIBLE)
          && access.pvpEnabled(MutationType.TROPHY_CRUCIBLE);
      boolean gravebloomPvp = snapshot.expressed().contains(MutationType.GRAVEBLOOM)
          && access.pvpEnabled(MutationType.GRAVEBLOOM);
      if (!trophyPvp && !gravebloomPvp) {
        return;
      }
    }
    stageDeathMutationGrant(
        request.targetId(),
        request.sourceEntityId(),
        attacker.getUniqueId(),
        request.playerTarget(),
        snapshot
    );
  }

  private void stageDeathMutationGrant(
      UUID targetId,
      UUID sourceEntityId,
      UUID ownerId,
      boolean playerTarget,
      MutationSnapshot snapshot
  ) {
    if (targetId == null || ownerId == null || sourceEntityId == null || playerTarget || snapshot == null) {
      return;
    }
    boolean trophy = snapshot.expressed().contains(MutationType.TROPHY_CRUCIBLE);
    boolean gravebloom = snapshot.expressed().contains(MutationType.GRAVEBLOOM);
    if (!trophy && !gravebloom) {
      store.deathMutationGrants.remove(targetId);
      return;
    }
    long now = System.currentTimeMillis();
    if (store.deathMutationGrants.size() >= MAX_DEATH_MUTATION_GRANTS) {
      pruneDeathMutationGrants(now);
      if (store.deathMutationGrants.size() >= MAX_DEATH_MUTATION_GRANTS) {
        return;
      }
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(ownerId);
    long loadoutGeneration;
    synchronized (runtime) {
      loadoutGeneration = runtime.loadoutGeneration;
    }
    store.deathMutationGrants.put(targetId, new MutationRuntimeStore.DeathMutationGrant(
        ownerId,
        sourceEntityId,
        loadoutGeneration,
        trophy,
        gravebloom,
        now + DEATH_MUTATION_GRANT_MILLIS
    ));
  }

  private boolean validDeathMutationGrant(
      LivingEntity dead,
      MutationRuntimeStore.DeathMutationGrant grant
  ) {
    if (grant == null || !grant.active(System.currentTimeMillis())) {
      return false;
    }
    EntityDamageEvent lastDamage = dead.getLastDamageCause();
    if (!(lastDamage instanceof EntityDamageByEntityEvent byEntity)
        || !grant.sourceEntityId().equals(byEntity.getDamager().getUniqueId())) {
      return false;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(grant.ownerId());
    if (runtime == null) {
      return false;
    }
    synchronized (runtime) {
      return runtime.loadoutGeneration == grant.loadoutGeneration();
    }
  }

  private void pruneDeathMutationGrants(long now) {
    int inspected = 0;
    Iterator<Map.Entry<UUID, MutationRuntimeStore.DeathMutationGrant>> iterator =
        store.deathMutationGrants.entrySet().iterator();
    while (iterator.hasNext() && inspected < DEATH_GRANT_PRUNE_BUDGET) {
      Map.Entry<UUID, MutationRuntimeStore.DeathMutationGrant> entry = iterator.next();
      if (!entry.getValue().active(now)) {
        store.deathMutationGrants.remove(entry.getKey(), entry.getValue());
      }
      inspected++;
    }
  }

  private boolean eligibleNaturalDeath(LivingEntity entity, MutationRuntimeStore.SpawnProvenance spawn) {
    if (!(entity instanceof Monster) && !(entity instanceof Slime)) {
      return false;
    }
    if (entity instanceof Tameable tameable && tameable.getOwnerUniqueId() != null) {
      return false;
    }
    return spawn != null && spawn.natural();
  }

  private boolean isFarmed(LivingEntity entity, String family) {
    Location location = entity.getLocation();
    FarmKey key = new FarmKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4, family);
    long now = System.currentTimeMillis();
    Deque<Long> existing = farmDeaths.get(key);
    if (existing == null && farmDeaths.size() >= MAX_FARM_KEYS) {
      pruneFarmDeaths(now);
      if (farmDeaths.size() >= MAX_FARM_KEYS) {
        return true;
      }
    }
    Deque<Long> deaths = farmDeaths.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    synchronized (deaths) {
      deaths.removeIf(timestamp -> timestamp < now - FARM_WINDOW_MILLIS);
      deaths.addLast(now);
      if (deaths.size() > FARM_DEATH_LIMIT) {
        return true;
      }
    }
    return false;
  }

  private void pruneFarmDeaths(long now) {
    synchronized (farmPruneLock) {
      if (farmPruneIterator == null || !farmPruneIterator.hasNext()) {
        farmPruneIterator = farmDeaths.entrySet().iterator();
      }
      int inspected = 0;
      while (farmPruneIterator.hasNext() && inspected < FARM_PRUNE_BUDGET) {
        Map.Entry<FarmKey, Deque<Long>> entry = farmPruneIterator.next();
        Deque<Long> deaths = entry.getValue();
        boolean empty;
        synchronized (deaths) {
          deaths.removeIf(timestamp -> timestamp < now - FARM_WINDOW_MILLIS);
          empty = deaths.isEmpty();
        }
        if (empty) {
          farmDeaths.remove(entry.getKey(), deaths);
        }
        inspected++;
      }
    }
  }

  private void pruneSpawnProvenance() {
    long cutoff = System.currentTimeMillis() - 3_600_000L;
    int inspected = 0;
    Iterator<Map.Entry<UUID, MutationRuntimeStore.SpawnProvenance>> iterator =
        store.spawnProvenance.entrySet().iterator();
    while (iterator.hasNext() && inspected < SPAWN_PRUNE_BUDGET) {
      Map.Entry<UUID, MutationRuntimeStore.SpawnProvenance> entry = iterator.next();
      if (entry.getValue().recordedAt() < cutoff) {
        store.spawnProvenance.remove(entry.getKey(), entry.getValue());
      }
      inspected++;
    }
  }

  private void tagNaturalTrophy(List<ItemStack> drops, String family) {
    for (ItemStack drop : drops) {
      if (drop == null || drop.getType().isAir()) {
        continue;
      }
      ItemMeta meta = drop.getItemMeta();
      meta.getPersistentDataContainer().set(trophyFamilyKey, PersistentDataType.STRING, family);
      drop.setItemMeta(meta);
      return;
    }
  }

  private void maintainTrophyPursuit(
      Player player,
      Mob mob,
      String family,
      Location playerLocation,
      double range
  ) {
    if (!mob.isValid() || mob.isDead() || (!(mob instanceof Monster) && !(mob instanceof Slime))
        || mob.getWorld() != playerLocation.getWorld()
        || mob.getLocation().distanceSquared(playerLocation) > range * range
        || !family.equals(familyOf(mob))) {
      return;
    }
    if (mob instanceof Tameable tameable && tameable.getOwnerUniqueId() != null) {
      return;
    }
    LivingEntity currentTarget = mob.getTarget();
    if (currentTarget != null && currentTarget != player) {
      return;
    }
    mob.setTarget(player);
  }

  private String trophyFamily(ItemStack item) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
      return null;
    }
    return item.getItemMeta().getPersistentDataContainer().get(trophyFamilyKey, PersistentDataType.STRING);
  }

  private String familyOf(Entity entity) {
    String name = entity.getType().name();
    if (name.contains("SKELETON") || name.contains("ZOMBIE") || name.contains("PHANTOM") || name.contains("WITHER")) {
      return "undead";
    }
    if (name.contains("SPIDER") || name.contains("SILVERFISH") || name.contains("ENDERMITE")) {
      return "arthropod";
    }
    if (name.contains("GUARDIAN") || name.contains("DROWNED") || name.contains("FISH")) {
      return "aquatic";
    }
    if (name.contains("BLAZE") || name.contains("PIGLIN") || name.contains("GHAST") || name.contains("MAGMA")) {
      return "nether";
    }
    if (name.contains("GOLEM") || name.contains("SHULKER")) {
      return "construct";
    }
    if (entity instanceof Slime) {
      return "slime";
    }
    return "beast";
  }

  private MutationUtilityTag utilityForFamily(String family) {
    if (family == null) {
      return MutationUtilityTag.NONE;
    }
    return switch (family) {
      case "undead" -> MutationUtilityTag.REGENERATION_SUPPRESSION;
      case "arthropod" -> MutationUtilityTag.PINNING;
      case "aquatic" -> MutationUtilityTag.BUOYANCY;
      case "nether" -> MutationUtilityTag.HEAT;
      case "construct" -> MutationUtilityTag.INTERRUPTION;
      case "slime" -> MutationUtilityTag.DISPLACEMENT;
      default -> MutationUtilityTag.MARKING;
    };
  }

  private MutationWeaponFamily weaponFamily(Player attacker, Entity damager) {
    ItemStack item = attacker.getInventory().getItemInMainHand();
    return weaponFamilyFor(item == null ? null : item.getType(), damager instanceof Projectile);
  }

  static MutationWeaponFamily weaponFamilyFor(Material material, boolean projectile) {
    if (projectile) {
      return MutationWeaponFamily.RANGED;
    }
    if (material == null) {
      return MutationWeaponFamily.BODY;
    }
    String name = material.name();
    if (name.equals("AIR") || name.endsWith("_AIR")) {
      return MutationWeaponFamily.BODY;
    }
    if (name.endsWith("BOW") || name.equals("TRIDENT") || name.equals("CROSSBOW")) {
      return MutationWeaponFamily.RANGED;
    }
    if (name.endsWith("PICKAXE")) {
      return MutationWeaponFamily.TOOL;
    }
    if (name.endsWith("AXE") || name.endsWith("_SPEAR") || name.equals("MACE")) {
      return MutationWeaponFamily.HEAVY;
    }
    if (name.endsWith("SWORD")) {
      return MutationWeaponFamily.PRECISION;
    }
    return material.getMaxDurability() > 0 ? MutationWeaponFamily.TOOL : MutationWeaponFamily.BODY;
  }

  private MutationUtilityTag utilityFor(MutationWeaponFamily family) {
    return switch (family) {
      case PRECISION -> MutationUtilityTag.PRECISION;
      case HEAVY -> MutationUtilityTag.DISPLACEMENT;
      case RANGED -> MutationUtilityTag.PINNING;
      case BODY -> MutationUtilityTag.INTERRUPTION;
      case TOOL -> MutationUtilityTag.POSTURE_PRESSURE;
    };
  }

  private int attackAngleBucket(Player attacker, LivingEntity target, double bucketDegrees) {
    return attackAngleBucket(attacker.getLocation(), target.getLocation(), bucketDegrees);
  }

  private int attackAngleBucket(Location attacker, Location target, double bucketDegrees) {
    Vector relative = attacker.toVector().subtract(target.toVector());
    double angle = Math.toDegrees(Math.atan2(relative.getZ(), relative.getX()));
    return MutationRuntimePolicy.angleBucket(angle, (int) Math.round(bucketDegrees));
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

  static boolean activeTrophyReservation(
      String durableFamily,
      long durableExpiry,
      String reservedFamily,
      long reservedExpiry,
      long now
  ) {
    return reservedFamily != null
        && reservedFamily.equals(durableFamily)
        && reservedExpiry == durableExpiry
        && durableExpiry > now;
  }

  private record ProjectileHitStage(
      UUID targetId,
      UUID targetWorldId,
      Location targetLocation,
      Location projectileOrigin,
      boolean playerTarget
  ) {
  }

  private record ProjectileUtilityRequest(
      UUID actorId,
      ProjectileHitStage hit,
      MutationUtilityTag tag,
      double factor,
      MutationType sourceType,
      long loadoutGeneration,
      long sourceGeneration,
      long reservationGeneration,
      String reservedFamily,
      long reservedExpiry,
      int delayTicks
  ) {
  }

  private record ProjectileUtilityAuthorization(
      ProjectileUtilityRequest request,
      Location targetLocation,
      long authorizedAt
  ) {
  }

  private record ProjectileDeathGrantRequest(
      UUID targetId,
      UUID sourceEntityId,
      Location targetLocation,
      boolean playerTarget
  ) {
  }

  private record FarmKey(UUID worldId, int chunkX, int chunkZ, String family) {
  }
}
