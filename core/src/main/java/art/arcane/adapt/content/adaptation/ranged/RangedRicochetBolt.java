/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RangedRicochetBolt extends SimpleAdaptation<RangedRicochetBolt.Config> {
  private static final int MAX_RICOCHETS = 12;
  private static final ProjectileMemo<Integer> RICOCHET_COUNT = ProjectileMemo.create();
  private static final ProjectileMemo<Integer> RICOCHET_MAX = ProjectileMemo.create();
  private static final ProjectileMemo<Double> BONUS_DAMAGE = ProjectileMemo.create();
  private static final ProjectileMemo<RicochetProfile> RICOCHET_PROFILE = ProjectileMemo.create();

  private final Set<String> reportedTransferFailures = ConcurrentHashMap.newKeySet();

  public RangedRicochetBolt() {
    super("ranged-ricochet-bolt");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spectral_arrow"));
    setInterval(1400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spectral_arrow"))
        .key("challenge_ranged_ricochet_kills_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spectral_arrow"))
            .key("challenge_ranged_ricochet_kills_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_ricochet_kills_50", "ranged.ricochet-bolt.ricochet-kills", 50, 500);
    registerMilestone("challenge_ranged_ricochet_kills_500", "ranged.ricochet-bolt.ricochet-kills", 500, 2000);
  }

  public static RicochetImpact impactOf(ProjectileRef projectile) {
    if (projectile == null) {
      return new RicochetImpact(0, 0D);
    }
    return new RicochetImpact(
        RICOCHET_COUNT.getOrDefault(projectile, 0),
        BONUS_DAMAGE.getOrDefault(projectile, 0D)
    );
  }

  /** Credits the registered Ricochet Bolt for a kill made by a projectile that bounced {@code ricochetCount} times. */
  public static void rewardRicochetKill(PlayerRef player, Loc deathLocation, int ricochetCount) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    if (server == null || server.getSkillRegistry() == null) {
      return;
    }
    Skill<?> ranged = server.getSkillRegistry().getSkill("ranged");
    if (ranged == null) {
      return;
    }
    for (Adaptation<?> adaptation : ranged.getAdaptations()) {
      if (adaptation instanceof RangedRicochetBolt ricochet) {
        ricochet.rewardManualRicochetKill(player, deathLocation, ricochetCount);
        return;
      }
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SHOTS, RangedPreferences.ALL_PROJECTILES);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxRicochets(level), 1);
    statLore(v, Form.pc(getSpeedBonusPerRicochet(level), 0), 2);
    statLore(v, Form.f(getDamageBonusPerRicochet(level), 2), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef projectile = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || getMetadataProfile(projectile) != null
        || !(projectile.shooter() instanceof PlayerRef player)
        || !supportsRicochet(projectile)
        || !preference(player, RangedPreferences.SHOTS).accepts(projectile)
        || (!RangedProjectiles.isArrowLike(projectile) && !preferenceEnabled(player, RangedPreferences.ALL_PROJECTILES))) {
      return;
    }

    RicochetProfile profile = getActiveProfile(player);
    if (profile != null) {
      applyRicochetProfile(projectile, profile);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(ProjectileHitEvent e) {
    ProjectileRef projectile = e.projectile();
    if (!(projectile.shooter() instanceof PlayerRef p)) {
      return;
    }

    if (RangedHeartseeker.isSeekingProjectile(projectile)) {
      return;
    }

    RicochetProfile profile = getMetadataProfile(projectile);
    if (profile == null) {
      return;
    }

    BlockRef hitBlock = e.hitBlock();
    if (hitBlock == null || !supportsRicochet(projectile)) {
      return;
    }

    int ricochetCount = Math.max(0, RICOCHET_COUNT.getOrDefault(projectile, 0));
    int maxRicochets = Math.min(
        MAX_RICOCHETS,
        Math.max(0, RICOCHET_MAX.getOrDefault(projectile, profile.maximumRicochets()))
    );
    if (ricochetCount >= maxRicochets) {
      fx(hitBlock.center(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.2D)
          .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.2F);
      return;
    }

    Vec3 incoming = resolveIncomingVector(projectile);
    BlockFace hitFace = resolveHitFace(e, incoming);
    if (hitFace == null) {
      return;
    }
    Vec3 faceDirection = direction(hitFace);
    double currentBonusDamage = BONUS_DAMAGE.getOrDefault(projectile, 0D);
    RicochetTransition transition = profile.withMaximumRicochets(maxRicochets)
        .next(ricochetCount, currentBonusDamage, incoming, faceDirection);
    if (transition == null) {
      return;
    }

    Vec3 ricochetVelocity = transition.direction().multiply(transition.speed());

    Loc bounceLocation = projectile.location()
        .add(faceDirection.normalize().multiply(getConfig().spawnOffsetFromSurface))
        .add(transition.direction().multiply(getConfig().spawnOffsetAlongDirection));
    WorldRef bounceWorld = bounceLocation.world();
    int bounceChunkX = bounceLocation.blockX() >> 4;
    int bounceChunkZ = bounceLocation.blockZ() >> 4;
    if (bounceWorld == null || !bounceWorld.isChunkLoaded(bounceChunkX, bounceChunkZ)) {
      return;
    }
    Loc hitCenter = hitBlock.center();
    boolean foliaThreading = Tasks.isFoliaThreading();
    boolean destinationOwned = !foliaThreading
        || Tasks.isOwnedByCurrentRegion(bounceLocation);
    RicochetCarriedState carried = RicochetCarriedState.capture(projectile);
    if (!RicochetTransferRules.requiresRegionHandoff(foliaThreading, destinationOwned)) {
      RicochetReplacementTicket replacement = beginReplacement(projectile);
      if (replacement == null) {
        return;
      }
      ProjectileRef ricochet = spawnLocalRicochetProjectile(
          projectile,
          bounceLocation,
          ricochetVelocity,
          p,
          profile,
          transition.count(),
          maxRicochets,
          transition.bonusDamage(),
          carried
      );
      if (ricochet == null) {
        cancelReplacement(replacement);
        return;
      }
      if (!completeReplacement(replacement, ricochet)) {
        ricochet.remove();
        return;
      }

      emitRicochetFx(hitCenter, transition);
      Tasks.runEntity(p, () -> rewardRicochetOwned(p, transition));
      projectile.remove();
      return;
    }

    ProjectileSnapshot snapshot = projectile.snapshot();
    if (snapshot == null) {
      return;
    }
    RicochetProjectileTemplate primary = new RicochetProjectileTemplate(
        snapshot,
        bounceLocation,
        ricochetVelocity,
        p,
        profile,
        transition.count(),
        maxRicochets,
        transition.bonusDamage(),
        hitCenter,
        transition,
        carried
    );
    Loc fallbackLocation = resolveTransferFallbackOwned(
        projectile.location(),
        hitBlock,
        incoming
    );
    RicochetProjectileTemplate fallback = fallbackLocation == null
        ? null
        : primary.withSpawnLocation(fallbackLocation);
    RicochetReplacementTicket replacement = beginReplacement(projectile);
    if (replacement == null) {
      return;
    }
    RicochetTransferRequest request = new RicochetTransferRequest(
        primary,
        fallback,
        replacement
    );
    if (!Tasks.runAt(bounceLocation, () -> completeTransferOwned(request))) {
      cancelReplacement(replacement);
      return;
    }

    projectile.remove();
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.damager())
        || !(e.damager() instanceof ProjectileRef projectile)
        || !(projectile.shooter() instanceof PlayerRef p)
        || !BONUS_DAMAGE.has(projectile)) {
      return;
    }

    if (isProtectedFriendly(p, e.entity())) {
      return;
    }

    double bonusDamage = BONUS_DAMAGE.getOrDefault(projectile, 0D);
    if (bonusDamage > 0 && e.damage() > 0) {
      e.setDamage(e.damage() + bonusDamage);
      int count = Math.min(12, 4 + (int) Math.round(bonusDamage));
      int ricochets = Math.max(1, RICOCHET_COUNT.getOrDefault(projectile, 1));
      fx(e.entity(), FxPriority.COMBAT)
          .burst(Particles.CRIT_MAGIC, count, 0.3D)
          .sound(Sounds.ENTITY_ARROW_HIT, 0.7F, (float) Math.min(2.0, 1.2 + (ricochets * 0.12)));
    }
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    LastDamage lastDamage = e.entity().lastDamage();
    if (e.platformCancelled()
        || lastDamage == null
        || !(lastDamage.damager() instanceof ProjectileRef projectile)
        || RangedHeartseeker.isSeekingProjectile(projectile)
        || !RICOCHET_COUNT.has(projectile)
        || RICOCHET_COUNT.getOrDefault(projectile, 0) <= 0
        || !(projectile.shooter() instanceof PlayerRef shooter)) {
      return;
    }

    Loc deathLocation = e.entity().location();
    rewardManualRicochetKill(
        shooter,
        deathLocation,
        RICOCHET_COUNT.getOrDefault(projectile, 0)
    );
  }

  public void rewardManualRicochetKill(
      PlayerRef player,
      Loc deathLocation,
      int ricochetCount
  ) {
    if (player == null || deathLocation == null || ricochetCount <= 0) {
      return;
    }
    Tasks.runEntity(player, () -> rewardRicochetKillOwned(player, deathLocation));
  }

  private ProjectileRef spawnLocalRicochetProjectile(
      ProjectileRef source,
      Loc spawnLocation,
      Vec3 velocity,
      PlayerRef shooter,
      RicochetProfile profile,
      int count,
      int maximum,
      double bonusDamage,
      RicochetCarriedState carried
  ) {
    ProjectileSnapshot snapshot = source.snapshot();
    if (snapshot == null) {
      return null;
    }

    try {
      return snapshot.spawn(spawnLocation, velocity, shooter, (ProjectileRef target) -> {
        carried.apply(target);
        applyRicochetProfile(target, profile);
        applyRicochetState(target, count, maximum, bonusDamage);
      });
    } catch (RuntimeException exception) {
      reportTransferFailure("spawn local projectile", source.world(), spawnLocation, exception);
      return null;
    }
  }

  private RicochetReplacementTicket beginReplacement(ProjectileRef source) {
    try {
      return RicochetReplacementTicket.of(ProjectileReplacementRegistry.begin(source));
    } catch (RuntimeException | Error error) {
      reportTransferFailure(
          "begin projectile ownership transfer",
          source.world(),
          source.location(),
          error
      );
      return null;
    }
  }

  private boolean completeReplacement(
      RicochetReplacementTicket replacement,
      ProjectileRef target
  ) {
    try {
      return replacement.complete(target);
    } catch (RuntimeException | Error error) {
      reportTransferFailure(
          "complete projectile ownership transfer",
          target.world(),
          target.location(),
          error
      );
      return false;
    }
  }

  private void cancelReplacement(RicochetReplacementTicket replacement) {
    if (replacement == null) {
      return;
    }
    try {
      replacement.cancel();
    } catch (RuntimeException | Error error) {
      AdaptLog.warn("Failed to cancel a Ricochet Bolt projectile ownership transfer.");
      AdaptLog.error(error);
    }
  }

  private void completeTransferOwned(RicochetTransferRequest request) {
    if (!isRuntimeRegistered()) {
      cancelReplacement(request.replacement());
      recoverLostProjectileItem(request.primary());
      return;
    }
    RicochetTransferCompletion primary = completeTransferOwned(
        request.primary(),
        request.replacement()
    );
    if (primary == RicochetTransferCompletion.COMPLETED) {
      return;
    }
    if (primary == RicochetTransferCompletion.REPLACEMENT_REJECTED) {
      return;
    }
    RicochetProjectileTemplate fallback = request.fallback();
    if (fallback == null) {
      cancelReplacement(request.replacement());
      recoverLostProjectileItem(request.primary());
      return;
    }
    boolean scheduled = Tasks.runAt(fallback.spawnLocation(), () -> {
      RicochetTransferCompletion completion = completeTransferOwned(
          fallback,
          request.replacement()
      );
      if (completion == RicochetTransferCompletion.SPAWN_FAILED) {
        cancelReplacement(request.replacement());
        recoverLostProjectileItem(fallback);
      }
    });
    if (!scheduled) {
      cancelReplacement(request.replacement());
      recoverLostProjectileItem(request.primary());
    }
  }

  private void recoverLostProjectileItem(RicochetProjectileTemplate template) {
    if (!template.snapshot().type().equals(RangedProjectiles.TRIDENT)) {
      return;
    }
    ItemRef item = template.snapshot().item();
    if (item == null || item.isEmpty()) {
      return;
    }

    PlayerRef shooter = template.shooter();
    Loc dropLocation = template.hitCenter();
    boolean handedToShooter = Tasks.runEntity(shooter, () -> {
      if (shooter.isOnline() && shooter.inventory().addItem(item.copy()).isEmpty()) {
        return;
      }
      dropRecoveredItem(dropLocation, item);
    });
    if (!handedToShooter) {
      dropRecoveredItem(dropLocation, item);
    }
  }

  private void dropRecoveredItem(Loc location, ItemRef item) {
    Tasks.runAt(location, () -> {
      WorldRef world = location.world();
      if (world != null) {
        world.dropItemNaturally(location.position(), item.copy());
      }
    });
  }

  private RicochetTransferCompletion completeTransferOwned(
      RicochetProjectileTemplate template,
      RicochetReplacementTicket replacement
  ) {
    if (!isRuntimeRegistered()) {
      return RicochetTransferCompletion.SPAWN_FAILED;
    }
    Loc spawnLocation = template.spawnLocation();
    WorldRef world = spawnLocation.world();
    int chunkX = spawnLocation.blockX() >> 4;
    int chunkZ = spawnLocation.blockZ() >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)
        || (Tasks.isFoliaThreading()
        && !Tasks.isOwnedByCurrentRegion(world, chunkX, chunkZ))) {
      return RicochetTransferCompletion.SPAWN_FAILED;
    }

    ProjectileRef target;
    try {
      target = template.snapshot().spawn(spawnLocation, template.velocity(), template.shooter(),
          (ProjectileRef created) -> hydrateTemplateOwned(created, template));
      if (target == null) {
        return RicochetTransferCompletion.SPAWN_FAILED;
      }
    } catch (RuntimeException exception) {
      reportTransferFailure("complete region projectile transfer", world, spawnLocation, exception);
      return RicochetTransferCompletion.SPAWN_FAILED;
    }
    if (!completeReplacement(replacement, target)) {
      target.remove();
      return RicochetTransferCompletion.REPLACEMENT_REJECTED;
    }

    Loc hitCenter = template.hitCenter();
    Tasks.runAt(hitCenter, () -> emitRicochetFx(hitCenter, template.transition()));
    PlayerRef shooter = template.shooter();
    Tasks.runEntity(shooter, () -> rewardRicochetOwned(shooter, template.transition()));
    return RicochetTransferCompletion.COMPLETED;
  }

  private void hydrateTemplateOwned(ProjectileRef target, RicochetProjectileTemplate template) {
    template.carried().apply(target);
    applyRicochetProfile(target, template.profile());
    applyRicochetState(target, template.count(), template.maximum(), template.bonusDamage());
  }

  private void reportTransferFailure(
      String operation,
      WorldRef world,
      Loc location,
      Throwable throwable
  ) {
    String failureKey = operation + ":" + world.uid() + ":" + throwable.getClass().getName();
    if (!reportedTransferFailures.add(failureKey)) {
      return;
    }
    AdaptLog.warn("Failed to " + operation + " for Ricochet Bolt in " + world.name()
        + " at " + location.blockX() + "," + location.blockY() + ","
        + location.blockZ() + ".");
    AdaptLog.error(throwable);
  }

  private Loc resolveTransferFallbackOwned(Loc impact, BlockRef block, Vec3 incoming) {
    if (block == null || incoming.lengthSquared() <= 0.000001D) {
      return null;
    }
    Vec3 backward = incoming.normalize().multiply(-1D);
    Box occupied = bounds(block).expand(0.05D);
    for (double distance = 0.2D; distance <= 4D; distance += 0.2D) {
      Loc candidate = impact.add(backward.multiply(distance));
      if (!occupied.contains(candidate.position()) && isLoadedOwned(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static Box bounds(BlockRef block) {
    List<Box> boxes = block.collisionBoxes();
    if (boxes.isEmpty()) {
      return new Box(block.x(), block.y(), block.z(), block.x(), block.y(), block.z());
    }
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;
    for (Box box : boxes) {
      minX = Math.min(minX, box.minX());
      minY = Math.min(minY, box.minY());
      minZ = Math.min(minZ, box.minZ());
      maxX = Math.max(maxX, box.maxX());
      maxY = Math.max(maxY, box.maxY());
      maxZ = Math.max(maxZ, box.maxZ());
    }
    return new Box(minX, minY, minZ, maxX, maxY, maxZ);
  }

  private boolean isLoadedOwned(Loc location) {
    WorldRef world = location.world();
    if (world == null) {
      return false;
    }
    int chunkX = location.blockX() >> 4;
    int chunkZ = location.blockZ() >> 4;
    return world.isChunkLoaded(chunkX, chunkZ)
        && (!Tasks.isFoliaThreading()
        || Tasks.isOwnedByCurrentRegion(world, chunkX, chunkZ));
  }

  private Vec3 resolveIncomingVector(ProjectileRef projectile) {
    Vec3 liveVelocity = projectile.velocity();
    if (liveVelocity.lengthSquared() >= getConfig().minimumLiveVelocitySquared) {
      return liveVelocity;
    }

    Vec3 facing = projectile.location().direction();
    if (facing.lengthSquared() > 0.0000001) {
      return facing.normalize().multiply(Math.max(getConfig().minimumPostBounceSpeed, liveVelocity.length()));
    }

    return liveVelocity;
  }

  private boolean supportsRicochet(ProjectileRef projectile) {
    if (RangedProjectiles.isArrowLike(projectile)) {
      return true;
    }

    return getConfig().applyToAllProjectiles
        && (projectile.type().equals(RangedProjectiles.SNOWBALL)
        || projectile.type().equals(RangedProjectiles.EGG));
  }

  private BlockFace resolveHitFace(ProjectileHitEvent e, Vec3 incoming) {
    if (e.hitFace() != null) {
      return e.hitFace();
    }

    double ax = Math.abs(incoming.x());
    double ay = Math.abs(incoming.y());
    double az = Math.abs(incoming.z());

    if (ay >= ax && ay >= az) {
      return incoming.y() > 0 ? BlockFace.DOWN : BlockFace.UP;
    }

    if (ax >= az) {
      return incoming.x() > 0 ? BlockFace.WEST : BlockFace.EAST;
    }

    return incoming.z() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
  }

  static Vec3 direction(BlockFace face) {
    return new Vec3(face.dx(), face.dy(), face.dz());
  }

  private RicochetProfile getMetadataProfile(ProjectileRef projectile) {
    return RICOCHET_PROFILE.get(projectile);
  }

  RicochetProfile getActiveProfile(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return null;
    }
    return new RicochetProfile(
        getMaxRicochets(level),
        getSpeedBonusPerRicochet(level),
        getDamageBonusPerRicochet(level),
        Math.max(0D, getConfig().minRicochetVelocitySquared),
        Math.max(0D, getConfig().minimumPostBounceSpeed)
    );
  }

  void applyRicochetProfile(ProjectileRef projectile, RicochetProfile profile) {
    RICOCHET_PROFILE.set(projectile, profile);
  }

  void applyRicochetState(ProjectileRef projectile, int count, int maximum, double bonusDamage) {
    RICOCHET_COUNT.set(projectile, Math.max(0, count));
    RICOCHET_MAX.set(projectile, Math.max(0, maximum));
    BONUS_DAMAGE.set(projectile, Math.max(0D, bonusDamage));
  }

  void emitRicochetFx(Loc hitCenter, RicochetTransition transition) {
    Vec3 reflected = transition.direction();
    int count = transition.count();
    fx(hitCenter, FxPriority.COMBAT)
        .particle(Particles.ELECTRIC_SPARK, Math.max(1, getConfig().sparkParticleCount),
            0, 0, 0, getConfig().sparkSpread, 0.02D)
        .particle(Particles.CRIT_MAGIC, Math.max(1, getConfig().critParticleCount),
            0, 0, 0, getConfig().critSpread, 0.08D)
        .dustRing(new Rgb(180, 210, 255), 0.4D + (count * 0.15D), 12, 1.0F)
        .line(Particles.ELECTRIC_SPARK,
            hitCenter.x() + (reflected.x() * 1.2D),
            hitCenter.y() + (reflected.y() * 1.2D),
            hitCenter.z() + (reflected.z() * 1.2D),
            5)
        .chord(
            Sounds.BLOCK_ANVIL_HIT,
            0.85F,
            (float) Math.max(0.4D,
                getConfig().bouncePitchBase - (count * getConfig().bouncePitchDropPerRicochet)),
            Sounds.BLOCK_AMETHYST_BLOCK_HIT,
            0.9F,
            (float) Math.min(2D,
                getConfig().sparkPitchBase + (count * getConfig().sparkPitchRaisePerRicochet))
        );
  }

  void rewardRicochetOwned(PlayerRef player, RicochetTransition transition) {
    xp(player, getConfig().xpPerRicochet + (transition.count() * getConfig().xpPerRicochetStep));
    addStat(player, "ranged.ricochet-bolt.total-ricochets", 1);
  }

  private void rewardRicochetKillOwned(PlayerRef player, Loc deathLocation) {
    if (!player.isOnline() || !hasActiveAdaptation(player)) {
      return;
    }
    addStat(player, "ranged.ricochet-bolt.ricochet-kills", 1);
    Tasks.runAt(deathLocation, () -> fx(deathLocation, FxPriority.COMBAT)
        .dustRing(new Rgb(90, 220, 230), 1.0D, 16, 1.1F)
        .burst(Particles.FIREWORK, 8, 0.3D)
        .chord(
            Sounds.BLOCK_NOTE_BLOCK_BELL,
            0.6F,
            1.6F,
            Sounds.BLOCK_AMETHYST_BLOCK_CHIME,
            0.5F,
            2.0F
        ));
  }

  private int getMaxRicochets(int level) {
    return Math.min(
        MAX_RICOCHETS,
        Math.max(1, (int) Math.round(
            getConfig().maxRicochetsBase
                + (getLevelPercent(level) * getConfig().maxRicochetsFactor)
        ))
    );
  }

  private double getSpeedBonusPerRicochet(int level) {
    return Math.min(getConfig().maxSpeedBonusPerRicochet,
        getConfig().speedBonusPerRicochetBase + (getLevelPercent(level) * getConfig().speedBonusPerRicochetFactor));
  }

  private double getDamageBonusPerRicochet(int level) {
    return Math.min(getConfig().maxDamageBonusPerRicochet,
        getConfig().damageBonusPerRicochetBase + (getLevelPercent(level) * getConfig().damageBonusPerRicochetFactor));
  }

  public record RicochetImpact(int count, double bonusDamage) {
    public RicochetImpact {
      count = Math.min(MAX_RICOCHETS, Math.max(0, count));
      bonusDamage = Double.isFinite(bonusDamage) ? Math.max(0D, bonusDamage) : 0D;
    }
  }

  @ConfigDescription("Projectiles ricochet from block impacts with chained bounces, scaling speed, and bonus damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ricochets Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRicochetsBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ricochets Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRicochetsFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Bonus Per Ricochet Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedBonusPerRicochetBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Bonus Per Ricochet Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedBonusPerRicochetFactor = 0.27;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Speed Bonus Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxSpeedBonusPerRicochet = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Per Ricochet Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusPerRicochetBase = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Per Ricochet Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusPerRicochetFactor = 2.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Damage Bonus Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDamageBonusPerRicochet = 3.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Ricochet Velocity Squared for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minRicochetVelocitySquared = 0.09;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Live Velocity Squared for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumLiveVelocitySquared = 0.0004;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Post Bounce Speed for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumPostBounceSpeed = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spawn Offset From Surface for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spawnOffsetFromSurface = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spawn Offset Along Direction for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spawnOffsetAlongDirection = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spark Particle Count for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int sparkParticleCount = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spark Spread for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sparkSpread = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crit Particle Count for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int critParticleCount = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crit Spread for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double critSpread = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bounce Pitch Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bouncePitchBase = 1.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bounce Pitch Drop Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bouncePitchDropPerRicochet = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spark Pitch Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sparkPitchBase = 1.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spark Pitch Raise Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sparkPitchRaisePerRicochet = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRicochet = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ricochet Step for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRicochetStep = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allow ricochet behavior to apply to throwables (snowballs, eggs, pearls, potions, exp bottles) so all supported player projectiles can bounce.", impact = "True enables universal ricochet across most player-thrown projectiles.")
    boolean applyToAllProjectiles = true;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
