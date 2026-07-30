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
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RangedTrajectorySight extends SimpleAdaptation<RangedTrajectorySight.Config> {
  private static final String AIMED_SHOT_META = "adapt-trajectory-aimed";
  private static final double EPSILON = 0.0000001D;
  private static final int MAX_RENDER_PASSES_PER_WINDOW = 12;
  private static final int MAX_RAY_TRACES_PER_WINDOW = 512;
  private static final long BUDGET_WINDOW_NANOS = 50_000_000L;
  private static final TrajectorySightBudget RUNTIME_BUDGET = new TrajectorySightBudget(
      MAX_RENDER_PASSES_PER_WINDOW,
      MAX_RAY_TRACES_PER_WINDOW,
      BUDGET_WINDOW_NANOS
  );

  private final Map<UUID, Long> drawStartedMillis = new ConcurrentHashMap<>();
  private final Map<UUID, Entity> previewGlowTargets = new ConcurrentHashMap<>();
  private final Map<UUID, PreviewState> previewState = new ConcurrentHashMap<>();
  private final Map<UUID, AimingSession> activeSessions = new ConcurrentHashMap<>();
  private volatile RangedForce cachedRangedForce;
  private volatile RangedRicochetBolt cachedRicochetBolt;
  private volatile RangedHeartseeker cachedHeartseeker;

  public RangedTrajectorySight() {
    super("ranged-trajectory-sight");
    registerConfiguration(Config.class);
    setIcon(Material.SPYGLASS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_ranged_trajectory_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_ranged_trajectory_100", "ranged.trajectory-sight.kills-while-aiming", 100, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getVelocityMultiplier(level)), 1);
    statLore(v, Form.f(getSegments(level)), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    stopAimingSession(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    stopAimingSession(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    stopAimingSession(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerDropItemEvent e) {
    restartAfterItemChange(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerStopUsingItemEvent e) {
    stopAimingSession(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isItem(hand)) {
      return;
    }

    Material type = hand.getType();
    if (type != Material.BOW && type != Material.CROSSBOW) {
      return;
    }

    if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
      boolean wasDrawing = drawStartedMillis.containsKey(p.getUniqueId());
      drawStartedMillis.put(p.getUniqueId(), System.currentTimeMillis());
      startAimingSession(p);
      if (!wasDrawing) {
        Location tip = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6));
        fx(tip, FxPriority.TRANSITION)
            .particle(Particles.ENCHANTMENT_TABLE, 4, 0, 0, 0, 0.05D, 0.01D)
            .dustRing(Color.fromRGB(120, 225, 255), 0.4D, 12, 0.6F)
            .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.35F, 1.4F);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerItemHeldEvent e) {
    Player p = e.getPlayer();
    restartAfterItemChange(p);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerSwapHandItemsEvent e) {
    Player p = e.getPlayer();
    restartAfterItemChange(p);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    if (e.isSneaking()) {
      startIfAiming(p);
      return;
    }

    if (resolvePreviewContext(p) == null) {
      stopAimingSession(p);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityShootBowEvent e) {
    if (e.getEntity() instanceof Player p && hasActiveAdaptation(p)) {
      if (activeSessions.containsKey(p.getUniqueId()) && e.getProjectile() != null) {
        e.getProjectile().setMetadata(AIMED_SHOT_META, new FixedMetadataValue(Adapt.instance, true));
      }
      stopAimingSession(p);
      fx(p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6)), FxPriority.TRANSITION)
          .dustBurst(Color.fromRGB(120, 225, 255), 3, 0.1D, 0.6F)
          .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.3F, 0.8F);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    if (e.getEntity().getShooter() instanceof Player p
        && activeSessions.containsKey(p.getUniqueId())
        && hasActiveAdaptation(p)) {
      e.getEntity().setMetadata(AIMED_SHOT_META, new FixedMetadataValue(Adapt.instance, true));
    }
  }

  @EventHandler
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getKiller() instanceof Player p && hasActiveAdaptation(p)) {
      if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
          && dmg.getDamager() instanceof Projectile projectile
          && projectile.getShooter() == p
          && isAimedShot(projectile)) {
        addStat(p, "ranged.trajectory-sight.kills-while-aiming", 1);
        fx(e.getEntity().getLocation(), FxPriority.COMBAT)
            .burst(Particles.CRIT_MAGIC, 6, 0.3D)
            .chord(Sound.ENTITY_ARROW_HIT, 0.7F, 1.6F, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5F, 2.0F);
      }
    }
  }

  private boolean isAimedShot(Projectile projectile) {
    for (MetadataValue value : projectile.getMetadata(AIMED_SHOT_META)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return value.asBoolean();
      }
    }
    return false;
  }

  @Override
  public void unregister() {
    super.unregister();
    for (AimingSession session : activeSessions.values()) {
      stopAimingSession(session.owner);
    }
    activeSessions.clear();
    Adapt.instance.getViewerGlowCoordinator().clearLayer(ViewerGlowCoordinator.Layer.RANGED_TRAJECTORY_SIGHT);
  }

  private void restartAfterItemChange(Player player) {
    stopAimingSession(player);
    J.runEntity(player, () -> startIfAiming(player), 1);
  }

  private void startIfAiming(Player player) {
    if (player.isOnline() && hasActiveAdaptation(player) && resolvePreviewContext(player) != null) {
      startAimingSession(player);
    }
  }

  private void startAimingSession(Player player) {
    UUID playerId = player.getUniqueId();
    if (activeSessions.containsKey(playerId)) {
      return;
    }

    AimingSession session = new AimingSession(player, player.getWorld());
    AimingSession existing = activeSessions.putIfAbsent(playerId, session);
    if (existing != null) {
      return;
    }

    long token = session.gate.start();
    if (token < 0 || !scheduleSession(session, token, 1)) {
      stopAimingSession(player);
    }
  }

  private void runSession(AimingSession session, long token) {
    Player player = session.owner;
    UUID playerId = player.getUniqueId();
    if (!session.gate.isCurrent(token)
        || activeSessions.get(playerId) != session
        || !player.isOnline()
        || player.getWorld() != session.world) {
      stopAimingSession(player);
      return;
    }

    long now = System.currentTimeMillis();
    if (!updatePreview(session, token, now)) {
      return;
    }

    if (!scheduleSession(session, token, sessionDelayTicks())) {
      stopAimingSession(player);
    }
  }

  private boolean scheduleSession(AimingSession session, long token, int delayTicks) {
    return J.runEntity(session.owner, () -> runSession(session, token), Math.max(1, delayTicks));
  }

  private int sessionDelayTicks() {
    int intervalMillis = Math.max(75, Math.min(100, getConfig().activeSessionIntervalMillis));
    return Math.max(1, (int) Math.ceil(intervalMillis / 50.0D));
  }

  private boolean updatePreview(AimingSession session, long token, long now) {
    Player p = session.owner;
    UUID id = p.getUniqueId();
    int level = getActiveLevel(p);
    if (level <= 0) {
      stopAimingSession(p);
      return false;
    }

    PreviewContext context = resolvePreviewContext(p);
    if (context == null) {
      stopAimingSession(p);
      return false;
    }

    if (context.trigger() != PreviewTrigger.DRAWING_BOW) {
      drawStartedMillis.remove(p.getUniqueId());
    }

    ShotPreview shot = getShotPreview(p, context, level);
    if (shot == null) {
      clearPreviewGlow(p);
      return true;
    }

    if (!shouldRenderPreview(p, level, context, now)) {
      return true;
    }

    LivingEntity seekTarget = resolveSeekingTarget(p, context);
    if (seekTarget != null) {
      requestSeekingRender(session, token, level, context, shot, seekTarget);
      return true;
    }

    if (!RUNTIME_BUDGET.tryAcquireRender(id)) {
      return true;
    }

    Entity predictedHit = renderTrajectory(p, getRenderSegments(level), shot);
    updatePreviewGlow(p, predictedHit);
    previewState.put(id, PreviewState.capture(now, level, context, p.getEyeLocation()));
    return true;
  }

  private void requestSeekingRender(AimingSession session, long token, int level, PreviewContext context,
                                    ShotPreview shot, LivingEntity target) {
    if (!session.seekingSnapshotPending.compareAndSet(false, true)) {
      return;
    }

    boolean scheduled = J.runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        J.runEntity(session.owner, () -> session.seekingSnapshotPending.set(false));
        return;
      }

      Location targetPoint = target.getLocation().add(0, target.getHeight() * 0.6D, 0);
      SeekingTargetSnapshot snapshot = new SeekingTargetSnapshot(target.getUniqueId(), targetPoint);
      J.runEntity(session.owner, () -> completeSeekingRender(session, token, level, context, shot, snapshot));
    });
    if (!scheduled) {
      session.seekingSnapshotPending.set(false);
    }
  }

  private void completeSeekingRender(AimingSession session, long token, int level, PreviewContext context,
                                     ShotPreview shot, SeekingTargetSnapshot target) {
    session.seekingSnapshotPending.set(false);
    Player player = session.owner;
    UUID playerId = player.getUniqueId();
    if (!session.gate.isCurrent(token)
        || activeSessions.get(playerId) != session
        || !player.isOnline()
        || player.getWorld() != session.world
        || target.point().getWorld() != player.getWorld()
        || resolvePreviewContext(player) == null
        || !RUNTIME_BUDGET.tryAcquireRender(playerId)) {
      return;
    }

    renderSeekingTrajectory(player, getRenderSegments(level), shot.initialVelocity(), target);
    releasePreviewGlowFor(player, target.entityId());
    previewState.put(playerId, PreviewState.capture(System.currentTimeMillis(), level, context, player.getEyeLocation()));
  }

  private void stopAimingSession(Player player) {
    if (player == null) {
      return;
    }

    UUID playerId = player.getUniqueId();
    AimingSession session = activeSessions.remove(playerId);
    if (session != null) {
      session.gate.stop();
      session.seekingSnapshotPending.set(false);
    }
    RUNTIME_BUDGET.cancel(playerId);
    clearPreviewState(playerId);
    clearPreviewGlow(player);
  }

  private boolean shouldRenderPreview(Player p, int level, PreviewContext context, long now) {
    PreviewState state = previewState.get(p.getUniqueId());
    if (state == null) {
      return true;
    }

    long refreshEvery = Math.max(10L, getConfig().previewRenderIntervalMillis);
    if (now - state.renderedAtMs() >= refreshEvery) {
      return true;
    }

    if (state.level() != level) {
      return true;
    }

    Material material = context.item().getType();
    if (state.material() != material || state.trigger() != context.trigger()) {
      return true;
    }

    Location eye = p.getEyeLocation();
    if (angleDelta(eye.getYaw(), state.yaw()) >= Math.max(0.01D, getConfig().previewYawDeltaDegrees)) {
      return true;
    }

    if (angleDelta(eye.getPitch(), state.pitch()) >= Math.max(0.01D, getConfig().previewPitchDeltaDegrees)) {
      return true;
    }

    double dx = eye.getX() - state.x();
    double dy = eye.getY() - state.y();
    double dz = eye.getZ() - state.z();
    double movedSq = (dx * dx) + (dy * dy) + (dz * dz);
    return movedSq >= Math.max(0D, getConfig().previewPositionDeltaSquared);
  }

  private double angleDelta(float from, float to) {
    double delta = Math.abs((double) from - to) % 360D;
    return delta > 180D ? 360D - delta : delta;
  }

  private int getRenderSegments(int level) {
    int minSegments = Math.max(6, getConfig().minimumRenderedSegments);
    int maxSegments = Math.max(minSegments, getConfig().maxRenderedSegments);
    int segments = Math.max(minSegments, Math.min(getSegments(level), maxSegments));
    if (Adapt.instance.getTicker() == null) {
      return segments;
    }

    double load = Adapt.instance.getTicker().getWindowLoadPercent();
    if (load < Math.max(0D, getConfig().previewHighLoadPercent)) {
      return segments;
    }

    double scale = Math.max(0.2D, Math.min(1D, getConfig().previewHighLoadSegmentScale));
    int scaled = (int) Math.round(segments * scale);
    return Math.max(minSegments, Math.min(maxSegments, scaled));
  }

  private void clearPreviewState(UUID id) {
    if (id == null) {
      return;
    }

    drawStartedMillis.remove(id);
    previewState.remove(id);
  }

  private PreviewContext resolvePreviewContext(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();

    if (isDrawingBow(p, main)) {
      return new PreviewContext(main, PreviewTrigger.DRAWING_BOW);
    }

    if (!p.isSneaking()) {
      return null;
    }

    if (isSneakProjectile(main)) {
      return new PreviewContext(main, PreviewTrigger.SNEAK_PROJECTILE);
    }

    if (isSneakProjectile(off)) {
      return new PreviewContext(off, PreviewTrigger.SNEAK_PROJECTILE);
    }

    return null;
  }

  private ShotPreview getShotPreview(Player p, PreviewContext context, int level) {
    Material launchType = context.item().getType();
    double launchVelocity = getLaunchVelocity(p, launchType);
    if (launchVelocity <= 0.01) {
      return null;
    }

    BallisticsProfile profile = resolveBallisticsProfile(launchType);
    Vector direction = applyLaunchDirectionTuning(launchType, p.getEyeLocation().getDirection().clone());
    if (direction.lengthSquared() <= EPSILON) {
      return null;
    }

    Vector velocity = direction.normalize().multiply(launchVelocity * getVelocityMultiplier(level));
    RicochetPreview ricochet = RicochetPreview.disabled();
    if (!isHeartseekerPreview(p, context)) {
      velocity.multiply(getRangedForceLaunchMultiplier(p));
      ricochet = getRicochetPreview(p);
      if (!supportsRicochet(launchType, ricochet)) {
        ricochet = RicochetPreview.disabled();
      }
    }
    return new ShotPreview(velocity, ricochet, profile, context.trigger());
  }

  private boolean isHeartseekerPreview(Player player, PreviewContext context) {
    if (context.trigger() != PreviewTrigger.DRAWING_BOW
        || context.item().getType() != Material.BOW) {
      return false;
    }
    RangedHeartseeker heartseeker = getHeartseekerAdaptation();
    return heartseeker != null && heartseeker.getLockedTarget(player) != null;
  }

  private double getLaunchVelocity(Player p, Material type) {
    if (type == Material.SNOWBALL || type == Material.EGG || type == Material.ENDER_PEARL) {
      return getConfig().thrownProjectileVelocity;
    }

    if (type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.EXPERIENCE_BOTTLE) {
      return getConfig().thrownPotionVelocity;
    }

    if (type == Material.TRIDENT) {
      return getConfig().tridentVelocity;
    }

    if (type == Material.BOW) {
      double force = getBowForce(p);
      if (force <= 0) {
        return 0;
      }

      return force * 3.0;
    }

    if (type == Material.CROSSBOW) {
      return getConfig().crossbowVelocity;
    }

    return getConfig().fallbackVelocity;
  }

  private double getBowForce(Player p) {
    UUID id = p.getUniqueId();
    long now = System.currentTimeMillis();
    long start = drawStartedMillis.computeIfAbsent(id, k -> now);
    double chargeTicks = Math.max(0, (now - start) / 50.0);

    if (!p.isHandRaised() && p.isSneaking()) {
      chargeTicks = getConfig().sneakPreviewChargeTicks;
    }

    double force = chargeTicks / 20.0;
    force = (force * force + force * 2.0) / 3.0;
    return Math.min(1.0, force);
  }

  private Entity renderTrajectory(Player p, int segments, ShotPreview shot) {
    Location eye = p.getEyeLocation();
    Vector launchDirection = eye.getDirection().normalize();
    Location current = eye.clone().add(launchDirection.multiply(getConfig().previewStartOffset));
    Vector velocity = shot.initialVelocity().clone();
    Color trailColor = shot.trigger() == PreviewTrigger.DRAWING_BOW
        ? Color.fromRGB(120, 225, 255)
        : Color.fromRGB(255, 190, 125);
    double minDistanceSq = Math.max(0, getConfig().minPreviewDistanceFromEye);
    minDistanceSq *= minDistanceSq;
    double spacing = Math.max(0.25, getConfig().previewPointSpacing);
    double dotCarry = 0;
    int ricochets = 0;
    Entity hitEntity = null;

    for (int i = 0; i < segments; i++) {
      Vector step = velocity.clone();
      double stepLength = step.length();
      if (stepLength <= EPSILON) {
        return hitEntity;
      }

      Vector stepDirection = step.clone().normalize();
      Location from = current.clone();
      if (!RUNTIME_BUDGET.tryAcquireRayTrace()) {
        break;
      }
      RayTraceResult entityHit = p.getWorld().rayTraceEntities(current, stepDirection, stepLength, entity -> isValidPreviewTarget(p, entity));
      if (!RUNTIME_BUDGET.tryAcquireRayTrace()) {
        break;
      }
      RayTraceResult hit = p.getWorld().rayTraceBlocks(current, stepDirection, stepLength, FluidCollisionMode.NEVER, true);
      if (isEntityFirstHit(current, hit, entityHit)) {
        Entity target = entityHit.getHitEntity();
        if (target != null) {
          hitEntity = target;
        }

        if (entityHit.getHitPosition() != null) {
          current = entityHit.getHitPosition().toLocation(p.getWorld());
        } else if (target != null) {
          current = target.getLocation().clone();
        }

        if (areParticlesEnabled()) {
          drawImpactMarker(p, eye, current, stepDirection, Color.fromRGB(255, 90, 90), minDistanceSq);
        }
        break;
      }

      if (hit != null && hit.getHitBlock() != null) {
        current = hit.getHitPosition().toLocation(p.getWorld());
        if (areParticlesEnabled()) {
          drawImpactMarker(p, eye, current, stepDirection, Color.fromRGB(255, 236, 128), minDistanceSq);
        }

        RicochetPreview ricochet = shot.ricochetPreview();
        if (canRicochet(ricochet, ricochets, velocity)) {
          BlockFace hitFace = hit.getHitBlockFace() == null ? resolveHitFace(velocity) : hit.getHitBlockFace();
          if (hitFace == null) {
            break;
          }

          Vector reflectedDir = reflect(velocity.clone().normalize(), hitFace);
          if (reflectedDir.lengthSquared() <= EPSILON) {
            break;
          }

          reflectedDir.normalize();
          double nextSpeed = Math.max(ricochet.minimumPostBounceSpeed(), velocity.length()) * (1D + ricochet.speedBonusPerRicochet());
          velocity = reflectedDir.clone().multiply(nextSpeed);
          current.add(hitFace.getDirection().normalize().multiply(ricochet.spawnOffsetFromSurface()))
              .add(reflectedDir.clone().multiply(ricochet.spawnOffsetAlongDirection()));
          ricochets++;
          if (areParticlesEnabled()) {
            if (eye.distanceSquared(current) >= minDistanceSq) {
              float bounceSize = getScaledParticleSize(eye.distance(current), 1.15D);
              Particle.DustOptions bounce = new Particle.DustOptions(Color.fromRGB(170, 200, 255), bounceSize);
              p.spawnParticle(Particle.DUST, current, Math.max(1, getConfig().impactParticleCount), 0.02, 0.02, 0.02, 0.0, bounce);
            }
          }
          continue;
        }

        break;
      }

      current.add(step);
      if (areParticlesEnabled()) {
        dotCarry = drawDottedSegment(p, eye, from, current, trailColor, minDistanceSq, spacing, dotCarry);
      }

      velocity.multiply(shot.profile().dragFactor());
      velocity.setY(velocity.getY() - shot.profile().gravityStep());
    }

    if (areParticlesEnabled() && eye.distanceSquared(current) >= minDistanceSq) {
      float tipSize = getScaledParticleSize(eye.distance(current), 1.2D);
      Particle.DustOptions impact = new Particle.DustOptions(Color.fromRGB(255, 236, 128), tipSize);
      p.spawnParticle(Particle.DUST, current, 1, 0.0, 0.0, 0.0, 0.0, impact);
    }
    return hitEntity;
  }

  private double drawDottedSegment(Player p, Location eye, Location from, Location to, Color color, double minDistanceSq, double spacing, double carry) {
    Vector delta = to.toVector().subtract(from.toVector());
    double length = delta.length();
    if (length <= EPSILON) {
      return carry;
    }

    Vector direction = delta.multiply(1D / length);
    double placed = -1;
    for (double d = spacing - carry; d <= length; d += spacing) {
      placed = d;
      Location point = from.clone().add(direction.clone().multiply(d));
      if (eye.distanceSquared(point) < minDistanceSq) {
        continue;
      }

      float size = getScaledParticleSize(eye.distance(point), 1D);
      Particle.DustOptions trail = new Particle.DustOptions(color, size);
      p.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, trail);
    }

    return placed < 0 ? carry + length : length - placed;
  }

  private void drawImpactMarker(Player p, Location eye, Location impact, Vector incoming, Color color, double minDistanceSq) {
    if (eye.distanceSquared(impact) < minDistanceSq) {
      return;
    }

    float size = getScaledParticleSize(eye.distance(impact), 1.2D);
    Particle.DustOptions core = new Particle.DustOptions(color, (float) Math.min(getConfig().maxParticleSize, size * 1.25D));
    p.spawnParticle(Particle.DUST, impact, Math.max(1, getConfig().impactParticleCount), 0.02, 0.02, 0.02, 0.0, core);

    Vector normal = incoming.lengthSquared() <= EPSILON ? new Vector(0, 1, 0) : incoming.clone().normalize();
    Vector seed = Math.abs(normal.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
    Vector u = normal.clone().crossProduct(seed);
    if (u.lengthSquared() <= EPSILON) {
      u = new Vector(1, 0, 0);
    }
    u.normalize();
    Vector v = normal.clone().crossProduct(u).normalize();
    double radius = Math.max(0.1, getConfig().impactRingRadius);
    Particle.DustOptions ring = new Particle.DustOptions(color, size * 0.8F);
    for (int i = 0; i < 8; i++) {
      double angle = (Math.PI * 2D * i) / 8D;
      Location point = impact.clone()
          .add(u.clone().multiply(Math.cos(angle) * radius))
          .add(v.clone().multiply(Math.sin(angle) * radius));
      p.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, ring);
    }
  }

  private void renderSeekingTrajectory(Player p, int segments, Vector initialVelocity, SeekingTargetSnapshot target) {
    if (!areParticlesEnabled()) {
      return;
    }

    RangedHeartseeker heartseeker = getHeartseekerAdaptation();
    if (heartseeker == null) {
      return;
    }

    Location eye = p.getEyeLocation().clone();
    Location current = eye.clone().add(eye.getDirection().normalize().multiply(getConfig().previewStartOffset));
    Vector dir = initialVelocity.clone();
    if (dir.lengthSquared() <= EPSILON) {
      return;
    }

    RangedHeartseeker.Config seekerConfig = heartseeker.getConfig();
    double speed = HeartseekerFlightMath.seekSpeed(dir.length());
    dir.normalize();
    Vector arcOrigin = current.toVector();
    Vector arcLaunchDirection = dir.clone();
    double arcTraveled = 0D;
    double minDistanceSq = Math.max(0, getConfig().minPreviewDistanceFromEye);
    minDistanceSq *= minDistanceSq;
    double spacing = Math.max(0.25, getConfig().previewPointSpacing);
    double dotCarry = 0;
    Color seekColor = Color.fromRGB(255, 60, 80);
    int steps = Math.min(80, Math.max(24, segments * 2));

    for (int i = 0; i < steps; i++) {
      Vector toTarget = target.point().toVector().subtract(current.toVector());
      double distance = toTarget.length();
      if (distance <= Math.max(0.9D, speed * 0.75D)) {
        drawImpactMarker(p, eye, current.clone().add(toTarget), dir, seekColor, minDistanceSq);
        return;
      }

      Vector directDesired = toTarget.multiply(1D / distance);
      boolean initialArcActive = arcTraveled < Math.max(2D, seekerConfig.initialArcDistance);
      Vector desired = initialArcActive
          ? HeartseekerFlightMath.quadraticArcGuidance(
              arcOrigin,
              arcLaunchDirection,
              target.point().toVector(),
              arcTraveled,
              Math.max(1D, seekerConfig.initialArcControlDistance),
              Math.max(2D, seekerConfig.initialArcDistance)
          )
          : directDesired;
      double turnDegrees = !initialArcActive
          && distance <= Math.max(1D, seekerConfig.lungeRadius)
          ? seekerConfig.lungeTurnDegreesPerTick
          : seekerConfig.turnDegreesPerTick;
      dir = HeartseekerFlightMath.turnTowards(
          dir,
          desired,
          Math.toRadians(Math.max(1D, Math.min(90D, turnDegrees)))
      );

      double stepLength = Math.min(speed, distance);
      if (!RUNTIME_BUDGET.tryAcquireRayTrace()) {
        return;
      }
      if (p.getWorld().rayTraceBlocks(current, dir, stepLength, FluidCollisionMode.NEVER, true) != null) {
        Vector lifted = dir.clone().add(new Vector(0, 0.9, 0)).normalize();
        if (!RUNTIME_BUDGET.tryAcquireRayTrace()) {
          return;
        }
        if (p.getWorld().rayTraceBlocks(current, lifted, stepLength, FluidCollisionMode.NEVER, true) == null) {
          dir = lifted;
        } else {
          drawImpactMarker(p, eye, current, dir, seekColor, minDistanceSq);
          return;
        }
      }

      Location from = current.clone();
      current.add(dir.clone().multiply(stepLength));
      arcTraveled += stepLength;
      dotCarry = drawDottedSegment(p, eye, from, current, seekColor, minDistanceSq, spacing, dotCarry);
    }
  }

  private LivingEntity resolveSeekingTarget(Player p, PreviewContext context) {
    if (context.trigger() != PreviewTrigger.DRAWING_BOW || context.item().getType() != Material.BOW) {
      return null;
    }

    RangedHeartseeker heartseeker = getHeartseekerAdaptation();
    if (heartseeker == null || !heartseeker.isEnabled()) {
      return null;
    }

    if (getAdaptationLevel(p, heartseeker.getName()) <= 0) {
      return null;
    }

    return heartseeker.getLockedTarget(p);
  }

  private void releasePreviewGlowFor(Player p, UUID lockedTargetId) {
    Entity current = previewGlowTargets.get(p.getUniqueId());
    if (current == null) {
      return;
    }

    if (current.getUniqueId().equals(lockedTargetId)) {
      previewGlowTargets.remove(p.getUniqueId(), current);
      return;
    }

    updatePreviewGlow(p, null);
  }

  private BallisticsProfile resolveBallisticsProfile(Material type) {
    if (type == Material.SNOWBALL || type == Material.EGG || type == Material.ENDER_PEARL) {
      return new BallisticsProfile(getConfig().lightProjectileDragFactor, getConfig().lightProjectileGravityStep);
    }

    if (type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.EXPERIENCE_BOTTLE) {
      return new BallisticsProfile(getConfig().heavyProjectileDragFactor, getConfig().heavyProjectileGravityStep);
    }

    return new BallisticsProfile(getConfig().dragFactor, getConfig().gravityStep);
  }

  private Vector applyLaunchDirectionTuning(Material type, Vector direction) {
    if (type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.EXPERIENCE_BOTTLE) {
      direction.setY(direction.getY() - getConfig().heavyProjectilePitchDrop);
    }

    return direction;
  }

  private double getRangedForceLaunchMultiplier(Player p) {
    RangedForce force = getRangedForceAdaptation();
    if (force == null || !force.isEnabled() || !force.getSkill().isEnabled()) {
      return 1D;
    }

    int level = getAdaptationLevel(p, force.getName());
    if (level <= 0) {
      return 1D;
    }

    double levelPercent = Math.min(1D, Math.max(0D, (double) level / (double) Math.max(1, force.getMaxLevel())));
    double speedBonus = levelPercent * force.getConfig().speedFactor;
    return Math.max(0.1D, 1D + speedBonus);
  }

  private RicochetPreview getRicochetPreview(Player p) {
    RangedRicochetBolt ricochet = getRicochetAdaptation();
    if (ricochet == null || !ricochet.isEnabled() || !ricochet.getSkill().isEnabled()) {
      return RicochetPreview.disabled();
    }

    int level = getAdaptationLevel(p, ricochet.getName());
    if (level <= 0) {
      return RicochetPreview.disabled();
    }

    RangedRicochetBolt.Config cfg = ricochet.getConfig();
    double levelPercent = Math.min(1D, Math.max(0D, (double) level / (double) Math.max(1, ricochet.getMaxLevel())));
    int maxRicochets = Math.max(1, (int) Math.round(cfg.maxRicochetsBase + (levelPercent * cfg.maxRicochetsFactor)));
    double speedBonus = Math.min(cfg.maxSpeedBonusPerRicochet,
        cfg.speedBonusPerRicochetBase + (levelPercent * cfg.speedBonusPerRicochetFactor));
    return new RicochetPreview(
        true,
        maxRicochets,
        speedBonus,
        cfg.minRicochetVelocitySquared,
        cfg.minimumPostBounceSpeed,
        cfg.spawnOffsetFromSurface,
        cfg.spawnOffsetAlongDirection,
        cfg.applyToAllProjectiles
    );
  }

  private int getAdaptationLevel(Player p, String adaptationId) {
    PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("ranged");
    return line == null ? 0 : line.getAdaptationLevel(adaptationId);
  }

  private RangedForce getRangedForceAdaptation() {
    RangedForce cached = cachedRangedForce;
    if (cached != null) {
      return cached;
    }

    RangedForce found = resolveRangedAdaptation(RangedForce.class);
    if (found != null) {
      cachedRangedForce = found;
    }
    return found;
  }

  private RangedRicochetBolt getRicochetAdaptation() {
    RangedRicochetBolt cached = cachedRicochetBolt;
    if (cached != null) {
      return cached;
    }

    RangedRicochetBolt found = resolveRangedAdaptation(RangedRicochetBolt.class);
    if (found != null) {
      cachedRicochetBolt = found;
    }
    return found;
  }

  private RangedHeartseeker getHeartseekerAdaptation() {
    RangedHeartseeker cached = cachedHeartseeker;
    if (cached != null) {
      return cached;
    }

    RangedHeartseeker found = resolveRangedAdaptation(RangedHeartseeker.class);
    if (found != null) {
      cachedHeartseeker = found;
    }
    return found;
  }

  private <T> T resolveRangedAdaptation(Class<T> type) {
    Skill<?> ranged = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill("ranged");
    if (ranged == null) {
      return null;
    }

    for (Adaptation<?> adaptation : ranged.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        return type.cast(adaptation);
      }
    }

    return null;
  }

  private boolean canRicochet(RicochetPreview ricochet, int ricochetCount, Vector incomingVelocity) {
    return ricochet.enabled()
        && ricochetCount < ricochet.maxRicochets()
        && incomingVelocity.lengthSquared() >= ricochet.minRicochetVelocitySquared();
  }

  private float getScaledParticleSize(double distance, double multiplier) {
    double size = getConfig().particleSize + (Math.max(0D, distance) * getConfig().particleSizePerBlock);
    size *= multiplier;
    size = Math.max(0.05D, Math.min(getConfig().maxParticleSize, size));
    return (float) size;
  }

  private boolean isEntityFirstHit(Location from, RayTraceResult blockHit, RayTraceResult entityHit) {
    if (entityHit == null || entityHit.getHitEntity() == null) {
      return false;
    }

    if (blockHit == null || blockHit.getHitPosition() == null) {
      return true;
    }

    if (entityHit.getHitPosition() == null) {
      return false;
    }

    double blockDistSq = blockHit.getHitPosition().distanceSquared(from.toVector());
    double entityDistSq = entityHit.getHitPosition().distanceSquared(from.toVector());
    return entityDistSq <= blockDistSq;
  }

  private boolean isValidPreviewTarget(Player shooter, Entity entity) {
    return entity instanceof LivingEntity
        && entity.isValid()
        && !entity.isDead()
        && !entity.getUniqueId().equals(shooter.getUniqueId());
  }

  private void updatePreviewGlow(Player p, Entity target) {
    if (!getConfig().glowPredictedTarget) {
      clearPreviewGlow(p);
      return;
    }

    UUID viewerId = p.getUniqueId();
    Entity current = previewGlowTargets.get(viewerId);
    if (current != null && current == target && current.getEntityId() == target.getEntityId()) {
      return;
    }

    ViewerGlowCoordinator glowCoordinator = Adapt.instance.getViewerGlowCoordinator();
    if (glowCoordinator == null || !glowCoordinator.isAvailable()) {
      return;
    }

    if (current != null) {
      previewGlowTargets.remove(viewerId, current);
      J.runEntity(current, () -> glowCoordinator.unset(
          ViewerGlowCoordinator.Layer.RANGED_TRAJECTORY_SIGHT,
          current,
          p
      ));
    }

    if (target == null) {
      return;
    }

    previewGlowTargets.put(viewerId, target);
    J.runEntity(target, () -> {
      if (!target.isValid() || previewGlowTargets.get(viewerId) != target) {
        return;
      }
      if (glowCoordinator.set(
          ViewerGlowCoordinator.Layer.RANGED_TRAJECTORY_SIGHT,
          target,
          p,
          ChatColor.GOLD
      )) {
        fx(target, FxPriority.TRANSITION)
            .burst(Particles.CRIT_MAGIC, 3, 0.15D)
            .sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.3F, 2.0F);
      } else {
        previewGlowTargets.remove(viewerId, target);
      }
    });
  }

  private void clearPreviewGlow(Player p) {
    UUID viewerId = p.getUniqueId();
    Entity target = previewGlowTargets.remove(viewerId);
    if (target == null) {
      return;
    }

    ViewerGlowCoordinator glowCoordinator = Adapt.instance.getViewerGlowCoordinator();
    if (glowCoordinator == null) {
      return;
    }

    J.runEntity(target, () -> glowCoordinator.unset(
        ViewerGlowCoordinator.Layer.RANGED_TRAJECTORY_SIGHT,
        target,
        p
    ));
  }

  private boolean supportsRicochet(Material launchType, RicochetPreview ricochet) {
    if (!ricochet.enabled()) {
      return false;
    }

    if (launchType == Material.BOW || launchType == Material.CROSSBOW || launchType == Material.TRIDENT) {
      return true;
    }

    return ricochet.applyToAllProjectiles()
        && (launchType == Material.SNOWBALL
        || launchType == Material.EGG);
  }

  private boolean isDrawingBow(Player p, ItemStack main) {
    if (!isItem(main)) {
      return false;
    }

    Material type = main.getType();
    if (type != Material.BOW && type != Material.CROSSBOW) {
      return false;
    }

    if (!p.isHandRaised()) {
      return false;
    }

    ItemStack active = p.getActiveItem();
    return isItem(active) && active.getType() == type;
  }

  private boolean isSneakProjectile(ItemStack item) {
    if (!isItem(item)) {
      return false;
    }

    Material type = item.getType();
    return type == Material.BOW
        || type == Material.CROSSBOW
        || type == Material.TRIDENT
        || type == Material.SNOWBALL
        || type == Material.EGG
        || type == Material.ENDER_PEARL
        || type == Material.SPLASH_POTION
        || type == Material.LINGERING_POTION
        || type == Material.EXPERIENCE_BOTTLE;
  }

  private Vector reflect(Vector incoming, BlockFace face) {
    Vector normal = face.getDirection().normalize();
    double dot = incoming.dot(normal);
    return incoming.clone().subtract(normal.multiply(2D * dot));
  }

  private BlockFace resolveHitFace(Vector incoming) {
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

  private int getSegments(int level) {
    return Math.max(10, (int) Math.round(getConfig().segmentsBase + (getLevelPercent(level) * getConfig().segmentsFactor)));
  }

  private double getVelocityMultiplier(int level) {
    return Math.max(0.1, getConfig().velocityBase + (getLevelPercent(level) * getConfig().velocityFactor));
  }

  private enum PreviewTrigger {
    SNEAK_PROJECTILE,
    DRAWING_BOW
  }

  private record PreviewContext(ItemStack item, PreviewTrigger trigger) {
  }

  private record BallisticsProfile(double dragFactor, double gravityStep) {
  }

  private record ShotPreview(Vector initialVelocity,
                             RicochetPreview ricochetPreview,
                             BallisticsProfile profile,
                             PreviewTrigger trigger) {
  }

  private record RicochetPreview(boolean enabled, int maxRicochets,
                                 double speedBonusPerRicochet,
                                 double minRicochetVelocitySquared,
                                 double minimumPostBounceSpeed,
                                 double spawnOffsetFromSurface,
                                 double spawnOffsetAlongDirection,
                                 boolean applyToAllProjectiles) {
    private static RicochetPreview disabled() {
      return new RicochetPreview(false, 0, 0D, Double.MAX_VALUE, 0D, 0D, 0D, false);
    }
  }

  private record PreviewState(
      long renderedAtMs,
      int level,
      Material material,
      PreviewTrigger trigger,
      double x,
      double y,
      double z,
      float yaw,
      float pitch
  ) {
    private static PreviewState capture(long renderedAtMs, int level, PreviewContext context, Location eye) {
      return new PreviewState(
          renderedAtMs,
          level,
          context.item().getType(),
          context.trigger(),
          eye.getX(),
          eye.getY(),
          eye.getZ(),
          eye.getYaw(),
          eye.getPitch()
      );
    }
  }

  private record SeekingTargetSnapshot(UUID entityId, Location point) {
  }

  private static class AimingSession {
    private final Player owner;
    private final World world;
    private final TrajectorySightSessionGate gate = new TrajectorySightSessionGate();
    private final AtomicBoolean seekingSnapshotPending = new AtomicBoolean();

    private AimingSession(Player owner, World world) {
      this.owner = owner;
      this.world = world;
    }
  }

  @ConfigDescription("Preview ranged projectile flight while sneaking or drawing your shot.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Segments Base for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double segmentsBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Segments Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double segmentsFactor = 26;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Velocity Base for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double velocityBase = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Velocity Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double velocityFactor = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Gravity Step for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double gravityStep = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drag Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dragFactor = 0.99;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Drag factor used for lighter thrown projectiles (snowballs, eggs, pearls).", impact = "Higher values keep thrown arcs flatter and longer; lower values make them lose speed faster.")
    double lightProjectileDragFactor = 0.99;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Drag factor used for heavier thrown projectiles (potions, experience bottles).", impact = "Higher values keep heavier throws moving faster; lower values shorten their travel.")
    double heavyProjectileDragFactor = 0.99;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Gravity step used for lighter thrown projectiles (snowballs, eggs, pearls).", impact = "Higher values produce steeper arcs for light projectiles.")
    double lightProjectileGravityStep = 0.03;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Gravity step used for heavier thrown projectiles (potions, experience bottles).", impact = "Higher values cause potions and bottles to drop faster.")
    double heavyProjectileGravityStep = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crossbow Velocity for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double crossbowVelocity = 3.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Launch velocity used for trident previews while sneaking.", impact = "Higher values extend trident prediction distance.")
    double tridentVelocity = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Launch velocity used for light thrown projectile previews.", impact = "Higher values extend snowball, egg, and pearl prediction distance.")
    double thrownProjectileVelocity = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Launch velocity used for potion and experience bottle previews.", impact = "Higher values extend heavy throw prediction distance.")
    double thrownPotionVelocity = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional downward launch offset for heavy thrown projectile previews.", impact = "Higher values tilt potion and bottle trajectories downward more strongly.")
    double heavyProjectilePitchDrop = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fallback Velocity for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fallbackVelocity = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sneak Preview Charge Ticks for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sneakPreviewChargeTicks = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Particle Size for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double particleSize = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How much particle size grows per block of distance from the viewer.", impact = "Higher values make far trajectory points easier to see.")
    double particleSizePerBlock = 0.008;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum particle size used for the trajectory preview.", impact = "Caps distance scaling to prevent oversized particles.")
    double maxParticleSize = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Impact Particle Count for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int impactParticleCount = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Distance in blocks between trajectory preview dots.", impact = "Lower values draw a denser, more continuous line at higher particle cost.")
    double previewPointSpacing = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius of the ring marker drawn where the shot would land.", impact = "Higher values draw a larger, more visible impact ring.")
    double impactRingRadius = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum distance from the player's eye before preview particles are shown.", impact = "Higher values keep particles out of your sightline and reduce visual obstruction.")
    double minPreviewDistanceFromEye = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Offset forward from the eye where trajectory simulation begins.", impact = "Higher values start the preview further from your face.")
    double previewStartOffset = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Highlights the predicted hit target entity with per-player glow.", impact = "Enable to glow whichever entity the preview would hit first.")
    boolean glowPredictedTarget = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum milliseconds between preview renders for a player when aim and context have not changed.", impact = "Lower values make previews smoother but increase CPU and ray-trace load.")
    int previewRenderIntervalMillis = 75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between owner-local active aiming refreshes, clamped between 75 and 100.", impact = "Lower values request smoother previews; the server scheduler resolves this range to two ticks on standard 20 TPS timing.")
    int activeSessionIntervalMillis = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Yaw delta in degrees required to force a preview recompute before the normal render interval.", impact = "Lower values react to small camera turns; higher values reduce recompute frequency.")
    double previewYawDeltaDegrees = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Pitch delta in degrees required to force a preview recompute before the normal render interval.", impact = "Lower values react to small vertical aim changes; higher values reduce recompute frequency.")
    double previewPitchDeltaDegrees = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Movement distance squared required to force a preview recompute before the normal render interval.", impact = "Lower values recompute more while strafing; higher values reduce recomputes while moving.")
    double previewPositionDeltaSquared = 0.0125;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest number of simulation segments used when rendering a trajectory.", impact = "Higher values preserve long previews under load; lower values cut work more aggressively.")
    int minimumRenderedSegments = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on simulation segments used for rendering.", impact = "Lower values reduce ray-trace workload; higher values produce longer and more precise previews.")
    int maxRenderedSegments = 36;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticker load percentage at which trajectory segments are scaled down.", impact = "Lower values engage load-shedding sooner; higher values preserve preview fidelity longer.")
    double previewHighLoadPercent = 42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Segment scale applied once high-load shedding is active.", impact = "Lower values reduce trajectory compute cost more aggressively during load spikes.")
    double previewHighLoadSegmentScale = 0.7;

    public Config() {
      costFactor = 0.75;
      initialCost = 4;
    }
  }
}
