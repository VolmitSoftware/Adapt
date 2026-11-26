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
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.tick.Ticker;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerStopUsingItemEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.world.BlockHit;
import art.arcane.adapt.platform.world.RayHit;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RangedTrajectorySight extends SimpleAdaptation<RangedTrajectorySight.Config> {
  private static final ProjectileMemo<Boolean> AIMED_SHOT = ProjectileMemo.create();
  private static final double EPSILON = 0.0000001D;
  private static final int MAX_RENDER_PASSES_PER_WINDOW = 12;
  private static final int MAX_RAY_TRACES_PER_WINDOW = 512;
  private static final long BUDGET_WINDOW_NANOS = 50_000_000L;
  private static final TrajectorySightBudget RUNTIME_BUDGET = new TrajectorySightBudget(
      MAX_RENDER_PASSES_PER_WINDOW,
      MAX_RAY_TRACES_PER_WINDOW,
      BUDGET_WINDOW_NANOS
  );
  private static final Id BOW = Id.minecraft("bow");
  private static final Id CROSSBOW = Id.minecraft("crossbow");
  private static final Id TRIDENT = Id.minecraft("trident");
  private static final Id SNOWBALL = Id.minecraft("snowball");
  private static final Id EGG = Id.minecraft("egg");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Id SPLASH_POTION = Id.minecraft("splash_potion");
  private static final Id LINGERING_POTION = Id.minecraft("lingering_potion");
  private static final Id EXPERIENCE_BOTTLE = Id.minecraft("experience_bottle");

  private final Map<UUID, Long> drawStartedMillis = new ConcurrentHashMap<>();
  private final Map<UUID, EntityRef> previewGlowTargets = new ConcurrentHashMap<>();
  private final Map<UUID, PreviewState> previewState = new ConcurrentHashMap<>();
  private final Map<UUID, AimingSession> activeSessions = new ConcurrentHashMap<>();
  private volatile RangedForce cachedRangedForce;
  private volatile RangedRicochetBolt cachedRicochetBolt;
  private volatile RangedHeartseeker cachedHeartseeker;

  public RangedTrajectorySight() {
    super("ranged-trajectory-sight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_ranged_trajectory_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_ranged_trajectory_100", "ranged.trajectory-sight.kills-while-aiming", 100, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.GLOW, RangedPreferences.PREVIEW, RangedPreferences.IMPACT, RangedPreferences.TRAIL);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getVelocityMultiplier(level)), 1);
    statLore(v, Form.f(getSegments(level)), 2);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    stopAimingSession(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerChangedWorldEvent e) {
    stopAimingSession(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    stopAimingSession(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDropItemEvent e) {
    restartAfterItemChange(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerStopUsingItemEvent e) {
    stopAimingSession(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isItem(hand)) {
      return;
    }

    Id type = hand.type();
    if (!type.equals(BOW) && !type.equals(CROSSBOW)) {
      return;
    }

    if (e.action().isRightClick()) {
      boolean wasDrawing = drawStartedMillis.containsKey(p.uuid());
      drawStartedMillis.put(p.uuid(), System.currentTimeMillis());
      startAimingSession(p);
      if (!wasDrawing) {
        Loc eye = p.eyeLocation();
        Loc tip = eye.add(eye.direction().multiply(0.6));
        fx(tip, FxPriority.TRANSITION)
            .particle(Particles.ENCHANTMENT_TABLE, 4, 0, 0, 0, 0.05D, 0.01D)
            .dustRing(new Rgb(120, 225, 255), 0.4D, 12, 0.6F)
            .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.35F, 1.4F);
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent e) {
    PlayerRef p = e.player();
    restartAfterItemChange(p);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    restartAfterItemChange(p);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
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

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityShootBowEvent e) {
    if (e.shooter() instanceof PlayerRef p && hasActiveAdaptation(p)) {
      if (activeSessions.containsKey(p.uuid()) && e.projectile() != null) {
        AIMED_SHOT.set(e.projectile(), true);
      }
      stopAimingSession(p);
      Loc eye = p.eyeLocation();
      fx(eye.add(eye.direction().multiply(0.6)), FxPriority.TRANSITION)
          .dustBurst(new Rgb(120, 225, 255), 3, 0.1D, 0.6F)
          .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.3F, 0.8F);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    if (e.projectile().shooter() instanceof PlayerRef p
        && activeSessions.containsKey(p.uuid())
        && hasActiveAdaptation(p)) {
      AIMED_SHOT.set(e.projectile(), true);
    }
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    if (!e.platformCancelled() && e.entity().killer() instanceof PlayerRef p && hasActiveAdaptation(p)) {
      LastDamage lastDamage = e.entity().lastDamage();
      if (lastDamage != null
          && lastDamage.damager() instanceof ProjectileRef projectile
          && p.uuid().equals(projectile.shooterId())
          && isAimedShot(projectile)) {
        addStat(p, "ranged.trajectory-sight.kills-while-aiming", 1);
        fx(e.entity().location(), FxPriority.COMBAT)
            .burst(Particles.CRIT_MAGIC, 6, 0.3D)
            .chord(Sounds.ENTITY_ARROW_HIT, 0.7F, 1.6F, Sounds.BLOCK_NOTE_BLOCK_BELL, 0.5F, 2.0F);
      }
    }
  }

  private boolean isAimedShot(ProjectileRef projectile) {
    return AIMED_SHOT.getOrDefault(projectile, false);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    stopAimingSession(player.getPlayer());
    startIfAiming(player.getPlayer());
  }

  @Override
  public void unregister() {
    super.unregister();
    for (AimingSession session : activeSessions.values()) {
      stopAimingSession(session.owner);
    }
    activeSessions.clear();
    ViewerGlowPort.current().clearLayer(ViewerGlowPort.Layer.RANGED_TRAJECTORY_SIGHT);
  }

  private void restartAfterItemChange(PlayerRef player) {
    stopAimingSession(player);
    Tasks.runEntity(player, () -> startIfAiming(player), 1);
  }

  private void startIfAiming(PlayerRef player) {
    if (player.isOnline() && hasActiveAdaptation(player) && resolvePreviewContext(player) != null) {
      startAimingSession(player);
    }
  }

  private void startAimingSession(PlayerRef player) {
    UUID playerId = player.uuid();
    if (activeSessions.containsKey(playerId)) {
      return;
    }

    AimingSession session = new AimingSession(player, player.world());
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
    PlayerRef player = session.owner;
    UUID playerId = player.uuid();
    if (!session.gate.isCurrent(token)
        || activeSessions.get(playerId) != session
        || !player.isOnline()
        || !player.world().equals(session.world)) {
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
    return Tasks.runEntity(session.owner, () -> runSession(session, token), Math.max(1, delayTicks));
  }

  private int sessionDelayTicks() {
    int intervalMillis = Math.max(75, Math.min(100, getConfig().activeSessionIntervalMillis));
    return Math.max(1, (int) Math.ceil(intervalMillis / 50.0D));
  }

  private boolean updatePreview(AimingSession session, long token, long now) {
    PlayerRef p = session.owner;
    UUID id = p.uuid();
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
      drawStartedMillis.remove(p.uuid());
    }

    ShotPreview shot = getShotPreview(p, context, level);
    if (shot == null) {
      clearPreviewGlow(p);
      return true;
    }

    if (!shouldRenderPreview(p, level, context, now)) {
      return true;
    }

    LivingRef seekTarget = resolveSeekingTarget(p, context);
    if (seekTarget != null) {
      requestSeekingRender(session, token, level, context, shot, seekTarget);
      return true;
    }

    if (!RUNTIME_BUDGET.tryAcquireRender(id)) {
      return true;
    }

    EntityRef predictedHit = renderTrajectory(p, getRenderSegments(level), shot);
    updatePreviewGlow(p, predictedHit);
    previewState.put(id, PreviewState.capture(now, level, context, p.eyeLocation()));
    return true;
  }

  private void requestSeekingRender(AimingSession session, long token, int level, PreviewContext context,
                                    ShotPreview shot, LivingRef target) {
    if (!session.seekingSnapshotPending.compareAndSet(false, true)) {
      return;
    }

    boolean scheduled = Tasks.runEntity(target, () -> {
      if (!target.isValid() || target.isDead()) {
        Tasks.runEntity(session.owner, () -> session.seekingSnapshotPending.set(false));
        return;
      }

      Loc targetPoint = target.location().add(0, target.height() * 0.6D, 0);
      SeekingTargetSnapshot snapshot = new SeekingTargetSnapshot(target.uuid(), targetPoint);
      Tasks.runEntity(session.owner, () -> completeSeekingRender(session, token, level, context, shot, snapshot));
    });
    if (!scheduled) {
      session.seekingSnapshotPending.set(false);
    }
  }

  private void completeSeekingRender(AimingSession session, long token, int level, PreviewContext context,
                                     ShotPreview shot, SeekingTargetSnapshot target) {
    session.seekingSnapshotPending.set(false);
    PlayerRef player = session.owner;
    UUID playerId = player.uuid();
    if (!session.gate.isCurrent(token)
        || activeSessions.get(playerId) != session
        || !player.isOnline()
        || !player.world().equals(session.world)
        || !target.point().sameWorld(player.location())
        || resolvePreviewContext(player) == null
        || !RUNTIME_BUDGET.tryAcquireRender(playerId)) {
      return;
    }

    renderSeekingTrajectory(player, getRenderSegments(level), shot.initialVelocity(), target);
    releasePreviewGlowFor(player, target.entityId());
    previewState.put(playerId, PreviewState.capture(System.currentTimeMillis(), level, context, player.eyeLocation()));
  }

  private void stopAimingSession(PlayerRef player) {
    if (player == null) {
      return;
    }

    UUID playerId = player.uuid();
    AimingSession session = activeSessions.remove(playerId);
    if (session != null) {
      session.gate.stop();
      session.seekingSnapshotPending.set(false);
    }
    RUNTIME_BUDGET.cancel(playerId);
    clearPreviewState(playerId);
    clearPreviewGlow(player);
  }

  private boolean shouldRenderPreview(PlayerRef p, int level, PreviewContext context, long now) {
    PreviewState state = previewState.get(p.uuid());
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

    Id material = context.item().type();
    if (!state.material().equals(material) || state.trigger() != context.trigger()) {
      return true;
    }

    Loc eye = p.eyeLocation();
    if (angleDelta(eye.yaw(), state.yaw()) >= Math.max(0.01D, getConfig().previewYawDeltaDegrees)) {
      return true;
    }

    if (angleDelta(eye.pitch(), state.pitch()) >= Math.max(0.01D, getConfig().previewPitchDeltaDegrees)) {
      return true;
    }

    double dx = eye.x() - state.x();
    double dy = eye.y() - state.y();
    double dz = eye.z() - state.z();
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
    AdaptCore core = AdaptCore.current();
    Ticker ticker = core == null ? null : core.getTicker();
    if (ticker == null) {
      return segments;
    }

    double load = ticker.getWindowLoadPercent();
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

  private PreviewContext resolvePreviewContext(PlayerRef p) {
    RangedPreferences.Preview trigger = preference(p, RangedPreferences.PREVIEW);
    ItemRef main = p.inventory().mainHand();
    ItemRef off = p.inventory().offHand();

    if (trigger != RangedPreferences.Preview.SNEAKING && isDrawingBow(p, main)) {
      return new PreviewContext(main, PreviewTrigger.DRAWING_BOW);
    }

    if (trigger == RangedPreferences.Preview.DRAWING || !p.isSneaking()) {
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

  private ShotPreview getShotPreview(PlayerRef p, PreviewContext context, int level) {
    Id launchType = context.item().type();
    double launchVelocity = getLaunchVelocity(p, launchType);
    if (launchVelocity <= 0.01) {
      return null;
    }

    BallisticsProfile profile = resolveBallisticsProfile(launchType);
    Vec3 direction = applyLaunchDirectionTuning(launchType, p.eyeLocation().direction());
    if (direction.lengthSquared() <= EPSILON) {
      return null;
    }

    Vec3 velocity = direction.normalize().multiply(launchVelocity * getVelocityMultiplier(level));
    RicochetPreview ricochet = RicochetPreview.disabled();
    if (!isHeartseekerPreview(p, context)) {
      velocity = velocity.multiply(getRangedForceLaunchMultiplier(p, launchType));
      ricochet = getRicochetPreview(p, launchType);
      if (!supportsRicochet(launchType, ricochet)) {
        ricochet = RicochetPreview.disabled();
      }
    }
    return new ShotPreview(velocity, ricochet, profile, context.trigger());
  }

  private boolean isHeartseekerPreview(PlayerRef player, PreviewContext context) {
    if (context.trigger() != PreviewTrigger.DRAWING_BOW
        || !context.item().type().equals(BOW)) {
      return false;
    }
    RangedHeartseeker heartseeker = getHeartseekerAdaptation();
    return heartseeker != null && heartseeker.getLockedTarget(player) != null;
  }

  private double getLaunchVelocity(PlayerRef p, Id type) {
    if (type.equals(SNOWBALL) || type.equals(EGG) || type.equals(ENDER_PEARL)) {
      return getConfig().thrownProjectileVelocity;
    }

    if (type.equals(SPLASH_POTION) || type.equals(LINGERING_POTION) || type.equals(EXPERIENCE_BOTTLE)) {
      return getConfig().thrownPotionVelocity;
    }

    if (type.equals(TRIDENT)) {
      return getConfig().tridentVelocity;
    }

    if (type.equals(BOW)) {
      double force = getBowForce(p);
      if (force <= 0) {
        return 0;
      }

      return force * 3.0;
    }

    if (type.equals(CROSSBOW)) {
      return getConfig().crossbowVelocity;
    }

    return getConfig().fallbackVelocity;
  }

  private double getBowForce(PlayerRef p) {
    UUID id = p.uuid();
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

  private EntityRef renderTrajectory(PlayerRef p, int segments, ShotPreview shot) {
    TrajectoryInk ink = ink(p);
    Loc eye = p.eyeLocation();
    Vec3 launchDirection = eye.direction().normalize();
    Loc current = eye.add(launchDirection.multiply(getConfig().previewStartOffset));
    Vec3 velocity = shot.initialVelocity();
    Rgb trailColor = shot.trigger() == PreviewTrigger.DRAWING_BOW
        ? new Rgb(120, 225, 255)
        : new Rgb(255, 190, 125);
    double minDistanceSq = Math.max(0, getConfig().minPreviewDistanceFromEye);
    minDistanceSq *= minDistanceSq;
    double spacing = Math.max(0.25, getConfig().previewPointSpacing);
    double dotCarry = 0;
    int ricochets = 0;
    EntityRef hitEntity = null;
    WorldRef world = p.world();

    for (int i = 0; i < segments; i++) {
      Vec3 step = velocity;
      double stepLength = step.length();
      if (stepLength <= EPSILON) {
        return hitEntity;
      }

      Vec3 stepDirection = step.normalize();
      Loc from = current;
      if (!RUNTIME_BUDGET.tryAcquireRayTrace()) {
        break;
      }
      RayHit entityHit = world.rayTraceEntityHit(current.position(), stepDirection, stepLength, 0D,
          (EntityRef entity) -> isValidPreviewTarget(p, entity));
      if (!RUNTIME_BUDGET.tryAcquireRayTrace()) {
        break;
      }
      BlockHit hit = world.rayTraceBlockHit(current.position(), stepDirection, stepLength, false);
      if (isEntityFirstHit(current, hit, entityHit)) {
        EntityRef target = entityHit.entity();
        if (target != null) {
          hitEntity = target;
        }

        current = Loc.of(world, entityHit.position());

        if (areParticlesEnabled()) {
          ink.impact(eye, current, stepDirection, new Rgb(255, 90, 90), minDistanceSq);
        }
        break;
      }

      if (hit != null) {
        current = Loc.of(world, hit.position());
        if (areParticlesEnabled()) {
          ink.impact(eye, current, stepDirection, new Rgb(255, 236, 128), minDistanceSq);
        }

        RicochetPreview ricochet = shot.ricochetPreview();
        if (canRicochet(ricochet, ricochets, velocity)) {
          BlockFace hitFace = hit.face() == null ? resolveHitFace(velocity) : hit.face();
          if (hitFace == null) {
            break;
          }

          Vec3 reflectedDir = reflect(velocity.normalize(), hitFace);
          if (reflectedDir.lengthSquared() <= EPSILON) {
            break;
          }

          reflectedDir = reflectedDir.normalize();
          double nextSpeed = Math.max(ricochet.minimumPostBounceSpeed(), velocity.length()) * (1D + ricochet.speedBonusPerRicochet());
          velocity = reflectedDir.multiply(nextSpeed);
          current = current.add(RangedRicochetBolt.direction(hitFace).normalize().multiply(ricochet.spawnOffsetFromSurface()))
              .add(reflectedDir.multiply(ricochet.spawnOffsetAlongDirection()));
          ricochets++;
          if (areParticlesEnabled() && preferenceEnabled(p, RangedPreferences.IMPACT)) {
            if (eye.distanceSquared(current) >= minDistanceSq) {
              float bounceSize = ink.size(eye.distance(current), 1.15D);
              ink.dust(current, new Rgb(170, 200, 255), bounceSize, Math.max(1, getConfig().impactParticleCount), 0.02D);
            }
          }
          continue;
        }

        break;
      }

      current = current.add(step);
      if (areParticlesEnabled()) {
        dotCarry = ink.dotted(eye, from, current, trailColor, minDistanceSq, spacing, dotCarry);
      }

      velocity = velocity.multiply(shot.profile().dragFactor());
      velocity = velocity.withY(velocity.y() - shot.profile().gravityStep());
    }

    if (areParticlesEnabled() && preferenceEnabled(p, RangedPreferences.IMPACT) && eye.distanceSquared(current) >= minDistanceSq) {
      float tipSize = ink.size(eye.distance(current), 1.2D);
      ink.dust(current, new Rgb(255, 236, 128), tipSize, 1, 0.0D);
    }
    return hitEntity;
  }

  static void viewerDust(PlayerRef viewer, Loc at, Rgb color, float size, int count, double spread) {
    FxHost fx = AdaptCore.platform().fx();
    fx.particle(viewer, Particles.dust(color, size), at.x(), at.y(), at.z(), count, spread, spread, spread, 0.0D, false);
  }

  public TrajectoryInk.Style inkStyle() {
    Config config = getConfig();
    return new TrajectoryInk.Style(config.particleSize, config.particleSizePerBlock, config.maxParticleSize,
        config.impactParticleCount, config.impactRingRadius, config.minPreviewDistanceFromEye,
        config.previewPointSpacing, config.previewStartOffset);
  }

  private TrajectoryInk ink(PlayerRef p) {
    return new TrajectoryInk(p, inkStyle(), preferenceEnabled(p, RangedPreferences.TRAIL),
        preferenceEnabled(p, RangedPreferences.IMPACT));
  }

  private void renderSeekingTrajectory(PlayerRef p, int segments, Vec3 initialVelocity, SeekingTargetSnapshot target) {
    if (!areParticlesEnabled()) {
      return;
    }

    RangedHeartseeker heartseeker = getHeartseekerAdaptation();
    if (heartseeker == null) {
      return;
    }

    ink(p).seekingPath(segments, initialVelocity, target.point(), HeartseekerGuidance.Arc.of(heartseeker.getConfig()),
        RUNTIME_BUDGET::tryAcquireRayTrace);
  }

  private LivingRef resolveSeekingTarget(PlayerRef p, PreviewContext context) {
    if (context.trigger() != PreviewTrigger.DRAWING_BOW || !context.item().type().equals(BOW)) {
      return null;
    }

    RangedHeartseeker heartseeker = getHeartseekerAdaptation();
    if (heartseeker == null || !heartseeker.isEnabled()) {
      return null;
    }

    if (heartseeker.getActiveLevel(p) <= 0) {
      return null;
    }

    return heartseeker.getLockedTarget(p);
  }

  private void releasePreviewGlowFor(PlayerRef p, UUID lockedTargetId) {
    EntityRef current = previewGlowTargets.get(p.uuid());
    if (current == null) {
      return;
    }

    if (current.uuid().equals(lockedTargetId)) {
      previewGlowTargets.remove(p.uuid(), current);
      return;
    }

    updatePreviewGlow(p, null);
  }

  private BallisticsProfile resolveBallisticsProfile(Id type) {
    if (type.equals(SNOWBALL) || type.equals(EGG) || type.equals(ENDER_PEARL)) {
      return new BallisticsProfile(getConfig().lightProjectileDragFactor, getConfig().lightProjectileGravityStep);
    }

    if (type.equals(SPLASH_POTION) || type.equals(LINGERING_POTION) || type.equals(EXPERIENCE_BOTTLE)) {
      return new BallisticsProfile(getConfig().heavyProjectileDragFactor, getConfig().heavyProjectileGravityStep);
    }

    return new BallisticsProfile(getConfig().dragFactor, getConfig().gravityStep);
  }

  private Vec3 applyLaunchDirectionTuning(Id type, Vec3 direction) {
    if (type.equals(SPLASH_POTION) || type.equals(LINGERING_POTION) || type.equals(EXPERIENCE_BOTTLE)) {
      return direction.withY(direction.y() - getConfig().heavyProjectilePitchDrop);
    }

    return direction;
  }

  private double getRangedForceLaunchMultiplier(PlayerRef p, Id weapon) {
    RangedForce force = getRangedForceAdaptation();
    if (force == null || !force.isEnabled() || !force.getSkill().isEnabled()) {
      return 1D;
    }

    int level = force.preference(p, RangedPreferences.SHOTS).accepts(weapon) ? force.getActiveLevel(p) : 0;
    if (level <= 0) {
      return 1D;
    }

    double levelPercent = Math.min(1D, Math.max(0D, (double) level / (double) Math.max(1, force.getMaxLevel())));
    double speedBonus = levelPercent * force.getConfig().speedFactor;
    return Math.max(0.1D, 1D + speedBonus);
  }

  private RicochetPreview getRicochetPreview(PlayerRef p, Id weapon) {
    RangedRicochetBolt ricochet = getRicochetAdaptation();
    if (ricochet == null || !ricochet.isEnabled() || !ricochet.getSkill().isEnabled()) {
      return RicochetPreview.disabled();
    }

    int level = ricochet.preference(p, RangedPreferences.SHOTS).accepts(weapon) ? ricochet.getActiveLevel(p) : 0;
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
        cfg.applyToAllProjectiles && ricochet.preferenceEnabled(p, RangedPreferences.ALL_PROJECTILES)
    );
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
    Skill<?> ranged = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("ranged");
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

  private boolean canRicochet(RicochetPreview ricochet, int ricochetCount, Vec3 incomingVelocity) {
    return ricochet.enabled()
        && ricochetCount < ricochet.maxRicochets()
        && incomingVelocity.lengthSquared() >= ricochet.minRicochetVelocitySquared();
  }

  private boolean isEntityFirstHit(Loc from, BlockHit blockHit, RayHit entityHit) {
    if (entityHit == null || entityHit.entity() == null) {
      return false;
    }

    if (blockHit == null) {
      return true;
    }

    double blockDistSq = blockHit.position().distanceSquared(from.position());
    double entityDistSq = entityHit.position().distanceSquared(from.position());
    return entityDistSq <= blockDistSq;
  }

  private boolean isValidPreviewTarget(PlayerRef shooter, EntityRef entity) {
    return entity instanceof LivingRef
        && entity.isValid()
        && !entity.isDead()
        && !entity.uuid().equals(shooter.uuid());
  }

  private void updatePreviewGlow(PlayerRef p, EntityRef target) {
    if (!getConfig().glowPredictedTarget || !preferenceEnabled(p, RangedPreferences.GLOW)) {
      clearPreviewGlow(p);
      return;
    }

    UUID viewerId = p.uuid();
    EntityRef current = previewGlowTargets.get(viewerId);
    if (current != null && current.equals(target) && current.entityId() == target.entityId()) {
      return;
    }

    ViewerGlowPort glowCoordinator = ViewerGlowPort.current();
    if (!glowCoordinator.isAvailable()) {
      return;
    }

    if (current != null) {
      previewGlowTargets.remove(viewerId, current);
      Tasks.runEntity(current, () -> glowCoordinator.unset(
          ViewerGlowPort.Layer.RANGED_TRAJECTORY_SIGHT,
          current,
          p
      ));
    }

    if (target == null) {
      return;
    }

    previewGlowTargets.put(viewerId, target);
    Tasks.runEntity(target, () -> {
      if (!target.isValid() || !target.equals(previewGlowTargets.get(viewerId))) {
        return;
      }
      if (glowCoordinator.set(
          ViewerGlowPort.Layer.RANGED_TRAJECTORY_SIGHT,
          target,
          p,
          GlowColor.GOLD
      )) {
        fx(target, FxPriority.TRANSITION)
            .burst(Particles.CRIT_MAGIC, 3, 0.15D)
            .sound(Sounds.BLOCK_NOTE_BLOCK_PLING, 0.3F, 2.0F);
      } else {
        previewGlowTargets.remove(viewerId, target);
      }
    });
  }

  private void clearPreviewGlow(PlayerRef p) {
    UUID viewerId = p.uuid();
    EntityRef target = previewGlowTargets.remove(viewerId);
    if (target == null) {
      return;
    }

    ViewerGlowPort glowCoordinator = ViewerGlowPort.current();
    Tasks.runEntity(target, () -> glowCoordinator.unset(
        ViewerGlowPort.Layer.RANGED_TRAJECTORY_SIGHT,
        target,
        p
    ));
  }

  private boolean supportsRicochet(Id launchType, RicochetPreview ricochet) {
    if (!ricochet.enabled()) {
      return false;
    }

    if (launchType.equals(BOW) || launchType.equals(CROSSBOW) || launchType.equals(TRIDENT)) {
      return true;
    }

    return ricochet.applyToAllProjectiles()
        && (launchType.equals(SNOWBALL)
        || launchType.equals(EGG));
  }

  /** Whether the player is drawing the bow or crossbow in {@code main}. */
  public static boolean isDrawingBow(PlayerRef p, ItemRef main) {
    if (main == null || main.type().isAir()) {
      return false;
    }

    Id type = main.type();
    if (!type.equals(BOW) && !type.equals(CROSSBOW)) {
      return false;
    }

    if (!p.isHandRaised()) {
      return false;
    }

    ItemRef active = p.activeItem();
    return active != null && !active.type().isAir() && active.type().equals(type);
  }

  private boolean isSneakProjectile(ItemRef item) {
    if (!isItem(item)) {
      return false;
    }

    Id type = item.type();
    return type.equals(BOW)
        || type.equals(CROSSBOW)
        || type.equals(TRIDENT)
        || type.equals(SNOWBALL)
        || type.equals(EGG)
        || type.equals(ENDER_PEARL)
        || type.equals(SPLASH_POTION)
        || type.equals(LINGERING_POTION)
        || type.equals(EXPERIENCE_BOTTLE);
  }

  private Vec3 reflect(Vec3 incoming, BlockFace face) {
    Vec3 normal = RangedRicochetBolt.direction(face).normalize();
    double dot = incoming.dot(normal);
    return incoming.subtract(normal.multiply(2D * dot));
  }

  private BlockFace resolveHitFace(Vec3 incoming) {
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

  private record PreviewContext(ItemRef item, PreviewTrigger trigger) {
  }

  private record BallisticsProfile(double dragFactor, double gravityStep) {
  }

  private record ShotPreview(Vec3 initialVelocity,
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
      Id material,
      PreviewTrigger trigger,
      double x,
      double y,
      double z,
      float yaw,
      float pitch
  ) {
    private static PreviewState capture(long renderedAtMs, int level, PreviewContext context, Loc eye) {
      return new PreviewState(
          renderedAtMs,
          level,
          context.item().type(),
          context.trigger(),
          eye.x(),
          eye.y(),
          eye.z(),
          eye.yaw(),
          eye.pitch()
      );
    }
  }

  private record SeekingTargetSnapshot(UUID entityId, Loc point) {
  }

  private static class AimingSession {
    private final PlayerRef owner;
    private final WorldRef world;
    private final TrajectorySightSessionGate gate = new TrajectorySightSessionGate();
    private final AtomicBoolean seekingSnapshotPending = new AtomicBoolean();

    private AimingSession(PlayerRef owner, WorldRef world) {
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
      baseCost = 1;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
