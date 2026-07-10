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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RangedRicochetBolt extends SimpleAdaptation<RangedRicochetBolt.Config> {
  private static final int MAX_RICOCHETS = 12;
  private static final String RICOCHET_COUNT_META = "adapt-ricochet-count";
  private static final String RICOCHET_MAX_META = "adapt-ricochet-max";
  private static final String BONUS_DAMAGE_META = "adapt-ricochet-bonus-damage";
  private static final String RICOCHET_PROFILE_META = "adapt-ricochet-profile";

  private final Set<String> reportedTransferFailures = ConcurrentHashMap.newKeySet();

  public RangedRicochetBolt() {
    super("ranged-ricochet-bolt");
    registerConfiguration(Config.class);
    setIcon(Material.SPECTRAL_ARROW);
    setInterval(1400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPECTRAL_ARROW)
        .key("challenge_ranged_ricochet_kills_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPECTRAL_ARROW)
            .key("challenge_ranged_ricochet_kills_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_ricochet_kills_50", "ranged.ricochet-bolt.ricochet-kills", 50, 500);
    registerMilestone("challenge_ranged_ricochet_kills_500", "ranged.ricochet-bolt.ricochet-kills", 500, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getMaxRicochets(level), 1);
    statLore(v, Form.pc(getSpeedBonusPerRicochet(level), 0), 2);
    statLore(v, Form.f(getDamageBonusPerRicochet(level), 2), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    Projectile projectile = e.getEntity();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || getMetadataProfile(projectile) != null
        || !(projectile.getShooter() instanceof Player player)
        || !supportsRicochet(projectile)) {
      return;
    }

    RicochetProfile profile = getActiveProfile(player);
    if (profile != null) {
      applyRicochetProfile(projectile, profile);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player p)) {
      return;
    }

    if (projectile.hasMetadata(RangedHeartseeker.SEEKING_ARROW_META)) {
      return;
    }

    RicochetProfile profile = getMetadataProfile(projectile);
    if (profile == null) {
      return;
    }

    if (e.getHitBlock() == null || !supportsRicochet(projectile)) {
      return;
    }

    int ricochetCount = Math.max(0, getMetadataInt(projectile, RICOCHET_COUNT_META, 0));
    int maxRicochets = Math.min(
        MAX_RICOCHETS,
        Math.max(0, getMetadataInt(
            projectile,
            RICOCHET_MAX_META,
            profile.maximumRicochets()
        ))
    );
    if (ricochetCount >= maxRicochets) {
      fx(e.getHitBlock().getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.2D)
          .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.2F);
      return;
    }

    Vector incoming = resolveIncomingVector(projectile);
    BlockFace hitFace = resolveHitFace(e, incoming);
    if (hitFace == null) {
      return;
    }
    double currentBonusDamage = getMetadataDouble(projectile, BONUS_DAMAGE_META, 0D);
    RicochetTransition transition = profile.withMaximumRicochets(maxRicochets)
        .next(ricochetCount, currentBonusDamage, incoming, hitFace.getDirection());
    if (transition == null) {
      return;
    }

    Vector ricochetVelocity = transition.direction().clone().multiply(transition.speed());

    Location bounceLocation = projectile.getLocation().clone()
        .add(hitFace.getDirection().normalize().multiply(getConfig().spawnOffsetFromSurface))
        .add(transition.direction().clone().multiply(getConfig().spawnOffsetAlongDirection));
    World bounceWorld = bounceLocation.getWorld();
    int bounceChunkX = bounceLocation.getBlockX() >> 4;
    int bounceChunkZ = bounceLocation.getBlockZ() >> 4;
    if (bounceWorld == null || !bounceWorld.isChunkLoaded(bounceChunkX, bounceChunkZ)) {
      return;
    }
    Location hitCenter = e.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
    boolean foliaThreading = J.isFoliaThreading();
    boolean destinationOwned = !foliaThreading
        || FoliaScheduler.isOwnedByCurrentRegion(bounceLocation);
    if (!RicochetTransferRules.requiresRegionHandoff(foliaThreading, destinationOwned)) {
      Projectile ricochet = spawnLocalRicochetProjectile(
          projectile,
          bounceLocation,
          ricochetVelocity,
          p,
          profile,
          transition.count(),
          maxRicochets,
          transition.bonusDamage()
      );
      if (ricochet == null) {
        return;
      }

      emitRicochetFx(hitCenter, transition);
      J.runEntity(p, () -> rewardRicochetOwned(p, transition));
      projectile.remove();
      return;
    }

    RicochetProjectileTemplate primary;
    try {
      primary = captureTransferOwned(
          projectile,
          bounceLocation,
          ricochetVelocity,
          p,
          profile,
          transition.count(),
          maxRicochets,
          transition.bonusDamage(),
          hitCenter,
          transition
      );
    } catch (IOException exception) {
      reportTransferFailure(
          "serialize projectile data",
          projectile.getWorld(),
          projectile.getLocation(),
          exception
      );
      return;
    }
    if (primary == null) {
      return;
    }
    Location fallbackLocation = resolveTransferFallbackOwned(
        projectile.getLocation(),
        e.getHitBlock(),
        incoming
    );
    RicochetProjectileTemplate fallback = fallbackLocation == null
        ? null
        : primary.withSpawnLocation(fallbackLocation);
    RicochetTransferRequest request = new RicochetTransferRequest(primary, fallback);
    if (!J.runAt(bounceLocation, () -> completeTransferOwned(request))) {
      return;
    }

    projectile.remove();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.getDamager())
        || !(e.getDamager() instanceof Projectile projectile)
        || !(projectile.getShooter() instanceof Player p)
        || !projectile.hasMetadata(BONUS_DAMAGE_META)) {
      return;
    }

    if (isProtectedFriendly(p, e.getEntity())) {
      return;
    }

    double bonusDamage = getMetadataDouble(projectile, BONUS_DAMAGE_META, 0D);
    if (bonusDamage > 0 && e.getDamage() > 0) {
      e.setDamage(e.getDamage() + bonusDamage);
      int count = Math.min(12, 4 + (int) Math.round(bonusDamage));
      int ricochets = Math.max(1, getMetadataInt(projectile, RICOCHET_COUNT_META, 1));
      fx(e.getEntity(), FxPriority.COMBAT)
          .burst(Particles.CRIT_MAGIC, count, 0.3D)
          .sound(Sound.ENTITY_ARROW_HIT, 0.7F, (float) Math.min(2.0, 1.2 + (ricochets * 0.12)));
    }
  }

  @EventHandler
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damage)
        || !(damage.getDamager() instanceof Projectile projectile)
        || RangedHeartseeker.isSeekingProjectile(projectile)
        || !projectile.hasMetadata(RICOCHET_COUNT_META)
        || getMetadataInt(projectile, RICOCHET_COUNT_META, 0) <= 0
        || !(projectile.getShooter() instanceof Player shooter)) {
      return;
    }

    Location deathLocation = e.getEntity().getLocation();
    J.runEntity(shooter, () -> rewardRicochetKillOwned(shooter, deathLocation));
  }

  private Projectile spawnLocalRicochetProjectile(
      Projectile source,
      Location spawnLocation,
      Vector velocity,
      Player shooter,
      RicochetProfile profile,
      int count,
      int maximum,
      double bonusDamage
  ) {
    RicochetProjectileKind kind = RicochetProjectileKind.resolve(source);
    if (kind == null) {
      return null;
    }

    try {
      return source.getWorld().spawn(
          spawnLocation,
          kind.projectileClass(),
          false,
          target -> {
            copyCommonStateOwned(source, target, shooter, velocity);
            copyPayloadOwned(source, target);
            applyRicochetProfile(target, profile);
            applyRicochetState(target, count, maximum, bonusDamage);
          }
      );
    } catch (RuntimeException exception) {
      reportTransferFailure("spawn local projectile", source.getWorld(), spawnLocation, exception);
      return null;
    }
  }

  private RicochetProjectileTemplate captureTransferOwned(
      Projectile source,
      Location spawnLocation,
      Vector velocity,
      Player shooter,
      RicochetProfile profile,
      int count,
      int maximum,
      double bonusDamage,
      Location hitCenter,
      RicochetTransition transition
  ) throws IOException {
    RicochetProjectileKind kind = RicochetProjectileKind.resolve(source);
    RicochetProjectilePayload payload = capturePayloadOwned(source);
    if (kind == null || payload == null) {
      return null;
    }

    byte[] persistentBytes = RicochetPersistentData.snapshot(source.getPersistentDataContainer());
    RicochetCommonState common = new RicochetCommonState(
        source.hasGravity(),
        source.getFireTicks(),
        source.hasLeftShooter(),
        source.hasBeenShot(),
        source.isPersistent(),
        source.getTicksLived(),
        source.getFallDistance(),
        source.getVisualFire(),
        source.getFreezeTicks(),
        source.isSilent(),
        source.isGlowing(),
        source.isInvulnerable(),
        source.isInvisible(),
        source.hasNoPhysics(),
        source.getPortalCooldown(),
        source.customName(),
        source.isCustomNameVisible(),
        source.isVisibleByDefault(),
        source.getScoreboardTags(),
        RangedHeavyDraw.readHeavyLevel(source),
        persistentBytes
    );
    return new RicochetProjectileTemplate(
        kind,
        source.getWorld(),
        spawnLocation,
        velocity,
        shooter,
        profile,
        count,
        maximum,
        bonusDamage,
        hitCenter,
        transition,
        common,
        payload
    );
  }

  private RicochetProjectilePayload capturePayloadOwned(Projectile source) {
    if (source instanceof SpectralArrow spectral) {
      return new RicochetSpectralPayload(captureArrowStateOwned(spectral), spectral.getGlowingTicks());
    }
    if (source instanceof Trident trident) {
      return new RicochetTridentPayload(
          captureArrowStateOwned(trident),
          trident.getItem(),
          trident.hasGlint(),
          trident.getLoyaltyLevel()
      );
    }
    if (source instanceof Arrow arrow) {
      return new RicochetArrowPayload(
          captureArrowStateOwned(arrow),
          arrow.getBasePotionType(),
          arrow.getColor(),
          arrow.getCustomEffects()
      );
    }
    if (source instanceof ThrowableProjectile throwable) {
      return new RicochetThrowablePayload(throwable.getItem());
    }
    return null;
  }

  private RicochetArrowState captureArrowStateOwned(AbstractArrow arrow) {
    return new RicochetArrowState(
        arrow.getDamage(),
        arrow.isCritical(),
        arrow.getPierceLevel(),
        arrow.getKnockbackStrength(),
        arrow.getPickupStatus(),
        RangedPiercing.isPiercingInitialized(arrow),
        RangedPiercing.readHits(arrow),
        arrow.getItemStack(),
        arrow.getWeapon(),
        arrow.getLifetimeTicks(),
        arrow.getHitSound()
    );
  }

  private void completeTransferOwned(RicochetTransferRequest request) {
    if (!isRuntimeRegistered()) {
      return;
    }
    if (completeTransferOwned(request.primary())) {
      return;
    }
    RicochetProjectileTemplate fallback = request.fallback();
    if (fallback != null) {
      J.runAt(fallback.spawnLocation(), () -> completeTransferOwned(fallback));
    }
  }

  private boolean completeTransferOwned(RicochetProjectileTemplate template) {
    if (!isRuntimeRegistered()) {
      return false;
    }
    Location spawnLocation = template.spawnLocation();
    World world = template.world();
    int chunkX = spawnLocation.getBlockX() >> 4;
    int chunkZ = spawnLocation.getBlockZ() >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)
        || (J.isFoliaThreading()
        && !FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ))) {
      return false;
    }

    Projectile target = null;
    try {
      target = world.createEntity(spawnLocation, template.kind().projectileClass());
      hydrateTemplateOwned(target, template);
      world.addEntity(target);
    } catch (IOException | RuntimeException exception) {
      if (target != null && target.isInWorld()) {
        target.remove();
      }
      reportTransferFailure("complete region projectile transfer", world, spawnLocation, exception);
      return false;
    }

    Location hitCenter = template.hitCenter();
    J.runAt(hitCenter, () -> emitRicochetFx(hitCenter, template.transition()));
    Player shooter = template.shooter();
    J.runEntity(shooter, () -> rewardRicochetOwned(shooter, template.transition()));
    return true;
  }

  private void hydrateTemplateOwned(Projectile target, RicochetProjectileTemplate template)
      throws IOException {
    applyCommonStateOwned(target, template.shooter(), template.velocity(), template.common());
    applyPayloadOwned(target, template.payload());
    applyRicochetProfile(target, template.profile());
    applyRicochetState(target, template.count(), template.maximum(), template.bonusDamage());
  }

  private void copyCommonStateOwned(
      Projectile source,
      Projectile target,
      Player shooter,
      Vector velocity
  ) {
    setShooterOwned(target, shooter);
    target.setGravity(source.hasGravity());
    target.setFireTicks(source.getFireTicks());
    target.setHasLeftShooter(source.hasLeftShooter());
    target.setHasBeenShot(source.hasBeenShot());
    target.setPersistent(source.isPersistent());
    target.setTicksLived(Math.max(1, source.getTicksLived()));
    target.setFallDistance(source.getFallDistance());
    target.setVisualFire(source.getVisualFire());
    target.setFreezeTicks(source.getFreezeTicks());
    target.setSilent(source.isSilent());
    target.setGlowing(source.isGlowing());
    target.setInvulnerable(source.isInvulnerable());
    target.setInvisible(source.isInvisible());
    target.setNoPhysics(source.hasNoPhysics());
    target.setPortalCooldown(source.getPortalCooldown());
    target.customName(source.customName());
    target.setCustomNameVisible(source.isCustomNameVisible());
    target.setVisibleByDefault(source.isVisibleByDefault());
    for (String tag : source.getScoreboardTags()) {
      target.addScoreboardTag(tag);
    }
    source.getPersistentDataContainer().copyTo(target.getPersistentDataContainer(), true);
    RangedHeavyDraw.copyHeavyState(source, target);
    target.setVelocity(velocity);
  }

  private void applyCommonStateOwned(
      Projectile target,
      Player shooter,
      Vector velocity,
      RicochetCommonState state
  ) throws IOException {
    setShooterOwned(target, shooter);
    target.setGravity(state.gravity());
    target.setFireTicks(state.fireTicks());
    target.setHasLeftShooter(state.leftShooter());
    target.setHasBeenShot(state.beenShot());
    target.setPersistent(state.persistent());
    target.setTicksLived(Math.max(1, state.ticksLived()));
    target.setFallDistance(state.fallDistance());
    target.setVisualFire(state.visualFire());
    target.setFreezeTicks(state.freezeTicks());
    target.setSilent(state.silent());
    target.setGlowing(state.glowing());
    target.setInvulnerable(state.invulnerable());
    target.setInvisible(state.invisible());
    target.setNoPhysics(state.noPhysics());
    target.setPortalCooldown(state.portalCooldown());
    target.customName(state.customName());
    target.setCustomNameVisible(state.customNameVisible());
    target.setVisibleByDefault(state.visibleByDefault());
    for (String tag : state.scoreboardTags()) {
      target.addScoreboardTag(tag);
    }
    byte[] persistentData = state.persistentData();
    RicochetPersistentData.restore(target.getPersistentDataContainer(), persistentData);
    RangedHeavyDraw.applyHeavyState(target, state.heavyDrawLevel());
    target.setVelocity(velocity);
  }

  private void setShooterOwned(Projectile target, Player shooter) {
    if (target instanceof AbstractArrow arrow) {
      arrow.setShooter(shooter, false);
      return;
    }
    target.setShooter(shooter);
  }

  private void copyPayloadOwned(Projectile source, Projectile target) {
    if (source instanceof AbstractArrow sourceArrow && target instanceof AbstractArrow targetArrow) {
      copyArrowStateOwned(sourceArrow, targetArrow);
    }
    if (source instanceof Arrow sourceArrow && target instanceof Arrow targetArrow) {
      targetArrow.setBasePotionType(sourceArrow.getBasePotionType());
      targetArrow.setColor(sourceArrow.getColor());
      for (PotionEffect effect : sourceArrow.getCustomEffects()) {
        targetArrow.addCustomEffect(effect, true);
      }
      return;
    }
    if (source instanceof SpectralArrow sourceSpectral
        && target instanceof SpectralArrow targetSpectral) {
      targetSpectral.setGlowingTicks(sourceSpectral.getGlowingTicks());
      return;
    }
    if (source instanceof Trident sourceTrident && target instanceof Trident targetTrident) {
      targetTrident.setItem(sourceTrident.getItem().clone());
      targetTrident.setGlint(sourceTrident.hasGlint());
      targetTrident.setLoyaltyLevel(sourceTrident.getLoyaltyLevel());
      targetTrident.setHasDealtDamage(false);
      return;
    }
    if (source instanceof ThrowableProjectile sourceThrowable
        && target instanceof ThrowableProjectile targetThrowable) {
      targetThrowable.setItem(sourceThrowable.getItem().clone());
    }
  }

  private void copyArrowStateOwned(AbstractArrow source, AbstractArrow target) {
    target.setDamage(source.getDamage());
    target.setCritical(source.isCritical());
    target.setPierceLevel(source.getPierceLevel());
    target.setKnockbackStrength(source.getKnockbackStrength());
    target.setPickupStatus(source.getPickupStatus());
    target.setItemStack(source.getItemStack().clone());
    ItemStack weapon = source.getWeapon();
    if (weapon != null) {
      target.setWeapon(weapon.clone());
    }
    target.setLifetimeTicks(source.getLifetimeTicks());
    target.setHitSound(source.getHitSound());
    RangedPiercing.copyPiercingState(source, target);
  }

  private void applyPayloadOwned(Projectile target, RicochetProjectilePayload payload) {
    if (payload instanceof RicochetArrowPayload arrowPayload && target instanceof Arrow arrow) {
      applyArrowStateOwned(arrow, arrowPayload.arrow());
      arrow.setBasePotionType(arrowPayload.basePotionType());
      arrow.setColor(arrowPayload.color());
      for (PotionEffect effect : arrowPayload.customEffects()) {
        arrow.addCustomEffect(effect, true);
      }
      return;
    }
    if (payload instanceof RicochetSpectralPayload spectralPayload
        && target instanceof SpectralArrow spectral) {
      applyArrowStateOwned(spectral, spectralPayload.arrow());
      spectral.setGlowingTicks(spectralPayload.glowingTicks());
      return;
    }
    if (payload instanceof RicochetTridentPayload tridentPayload
        && target instanceof Trident trident) {
      applyArrowStateOwned(trident, tridentPayload.arrow());
      trident.setItem(tridentPayload.item());
      trident.setGlint(tridentPayload.glint());
      trident.setLoyaltyLevel(tridentPayload.loyaltyLevel());
      trident.setHasDealtDamage(false);
      return;
    }
    if (payload instanceof RicochetThrowablePayload throwablePayload
        && target instanceof ThrowableProjectile throwable) {
      throwable.setItem(throwablePayload.item());
    }
  }

  private void applyArrowStateOwned(AbstractArrow target, RicochetArrowState state) {
    target.setDamage(state.damage());
    target.setCritical(state.critical());
    target.setPierceLevel(state.pierceLevel());
    target.setKnockbackStrength(state.knockbackStrength());
    target.setPickupStatus(state.pickupStatus());
    target.setItemStack(state.item());
    ItemStack weapon = state.weapon();
    if (weapon != null) {
      target.setWeapon(weapon);
    }
    target.setLifetimeTicks(state.lifetimeTicks());
    target.setHitSound(state.hitSound());
    RangedPiercing.applyPiercingState(
        target,
        state.piercingInitialized(),
        state.piercingHits()
    );
  }

  private void reportTransferFailure(
      String operation,
      World world,
      Location location,
      Throwable throwable
  ) {
    String failureKey = operation + ":" + world.getUID() + ":" + throwable.getClass().getName();
    if (!reportedTransferFailures.add(failureKey)) {
      return;
    }
    Adapt.warn("Failed to " + operation + " for Ricochet Bolt in " + world.getName()
        + " at " + location.getBlockX() + "," + location.getBlockY() + ","
        + location.getBlockZ() + ".");
    throwable.printStackTrace();
  }

  private Location resolveTransferFallbackOwned(Location impact, Block block, Vector incoming) {
    if (block == null || incoming.lengthSquared() <= 0.000001D) {
      return null;
    }
    Vector backward = incoming.clone().normalize().multiply(-1D);
    BoundingBox occupied = block.getBoundingBox().expand(0.05D);
    for (double distance = 0.2D; distance <= 4D; distance += 0.2D) {
      Location candidate = impact.clone().add(backward.clone().multiply(distance));
      if (!occupied.contains(candidate.toVector()) && isLoadedOwned(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private boolean isLoadedOwned(Location location) {
    World world = location.getWorld();
    if (world == null) {
      return false;
    }
    int chunkX = location.getBlockX() >> 4;
    int chunkZ = location.getBlockZ() >> 4;
    return world.isChunkLoaded(chunkX, chunkZ)
        && (!J.isFoliaThreading()
        || FoliaScheduler.isOwnedByCurrentRegion(world, chunkX, chunkZ));
  }

  private Vector resolveIncomingVector(Projectile projectile) {
    Vector liveVelocity = projectile.getVelocity().clone();
    if (liveVelocity.lengthSquared() >= getConfig().minimumLiveVelocitySquared) {
      return liveVelocity;
    }

    Vector facing = projectile.getLocation().getDirection().clone();
    if (facing.lengthSquared() > 0.0000001) {
      return facing.normalize().multiply(Math.max(getConfig().minimumPostBounceSpeed, liveVelocity.length()));
    }

    return liveVelocity;
  }

  private boolean supportsRicochet(Projectile projectile) {
    if (projectile instanceof AbstractArrow) {
      return true;
    }

    return getConfig().applyToAllProjectiles
        && (projectile instanceof Snowball
        || projectile instanceof Egg
        || projectile instanceof EnderPearl
        || projectile instanceof ThrownPotion
        || projectile instanceof ThrownExpBottle);
  }

  private BlockFace resolveHitFace(ProjectileHitEvent e, Vector incoming) {
    if (e.getHitBlockFace() != null) {
      return e.getHitBlockFace();
    }

    double ax = Math.abs(incoming.getX());
    double ay = Math.abs(incoming.getY());
    double az = Math.abs(incoming.getZ());

    if (ay >= ax && ay >= az) {
      return incoming.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
    }

    if (ax >= az) {
      return incoming.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
    }

    return incoming.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
  }

  private int getMetadataInt(Projectile projectile, String key, int fallback) {
    for (MetadataValue value : projectile.getMetadata(key)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return value.asInt();
      }
    }

    return fallback;
  }

  private RicochetProfile getMetadataProfile(Projectile projectile) {
    for (MetadataValue value : projectile.getMetadata(RICOCHET_PROFILE_META)) {
      if (value.getOwningPlugin() == Adapt.instance && value.value() instanceof RicochetProfile profile) {
        return profile;
      }
    }
    return null;
  }

  private double getMetadataDouble(Projectile projectile, String key, double fallback) {
    for (MetadataValue value : projectile.getMetadata(key)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return value.asDouble();
      }
    }

    return fallback;
  }

  RicochetProfile getActiveProfile(Player player) {
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

  void applyRicochetProfile(Projectile projectile, RicochetProfile profile) {
    projectile.setMetadata(RICOCHET_PROFILE_META, new FixedMetadataValue(Adapt.instance, profile));
  }

  void applyRicochetState(Projectile projectile, int count, int maximum, double bonusDamage) {
    projectile.setMetadata(RICOCHET_COUNT_META,
        new FixedMetadataValue(Adapt.instance, Math.max(0, count)));
    projectile.setMetadata(RICOCHET_MAX_META,
        new FixedMetadataValue(Adapt.instance, Math.max(0, maximum)));
    projectile.setMetadata(BONUS_DAMAGE_META,
        new FixedMetadataValue(Adapt.instance, Math.max(0D, bonusDamage)));
  }

  void emitRicochetFx(Location hitCenter, RicochetTransition transition) {
    Vector reflected = transition.direction();
    int count = transition.count();
    fx(hitCenter, FxPriority.COMBAT)
        .particle(Particle.ELECTRIC_SPARK, Math.max(1, getConfig().sparkParticleCount),
            0, 0, 0, getConfig().sparkSpread, 0.02D)
        .particle(Particles.CRIT_MAGIC, Math.max(1, getConfig().critParticleCount),
            0, 0, 0, getConfig().critSpread, 0.08D)
        .dustRing(Color.fromRGB(180, 210, 255), 0.4D + (count * 0.15D), 12, 1.0F)
        .line(Particle.ELECTRIC_SPARK,
            hitCenter.getX() + (reflected.getX() * 1.2D),
            hitCenter.getY() + (reflected.getY() * 1.2D),
            hitCenter.getZ() + (reflected.getZ() * 1.2D),
            5)
        .chord(
            Sound.BLOCK_ANVIL_HIT,
            0.85F,
            (float) Math.max(0.4D,
                getConfig().bouncePitchBase - (count * getConfig().bouncePitchDropPerRicochet)),
            Sound.BLOCK_AMETHYST_BLOCK_HIT,
            0.9F,
            (float) Math.min(2D,
                getConfig().sparkPitchBase + (count * getConfig().sparkPitchRaisePerRicochet))
        );
  }

  void rewardRicochetOwned(Player player, RicochetTransition transition) {
    xp(player, getConfig().xpPerRicochet + (transition.count() * getConfig().xpPerRicochetStep));
    addStat(player, "ranged.ricochet-bolt.total-ricochets", 1);
  }

  private void rewardRicochetKillOwned(Player player, Location deathLocation) {
    if (!player.isOnline() || !hasActiveAdaptation(player)) {
      return;
    }
    addStat(player, "ranged.ricochet-bolt.ricochet-kills", 1);
    J.runAt(deathLocation, () -> fx(deathLocation, FxPriority.COMBAT)
        .dustRing(Color.fromRGB(90, 220, 230), 1.0D, 16, 1.1F)
        .burst(Particles.FIREWORK, 8, 0.3D)
        .chord(
            Sound.BLOCK_NOTE_BLOCK_BELL,
            0.6F,
            1.6F,
            Sound.BLOCK_AMETHYST_BLOCK_CHIME,
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
      costFactor = 0.74;
      initialCost = 4;
    }
  }
}
