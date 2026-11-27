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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;


import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthSpeed extends SimpleAdaptation<StealthSpeed.Config> {
  public static final Id ID = Id.adapt("stealth/speed");
  public static final PlayerPreference<CommonPreferences.Toggle> STEP_UP = CommonPreferences.toggle("step-up", Text.key("adapt.stealthspeed.preferences.preference_step_up"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> STEP_DOWN = CommonPreferences.toggle("step-down", Text.key("adapt.stealthspeed.preferences.preference_step_down"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> PARTICLES = CommonPreferences.toggle("particles", Text.key("adapt.stealthspeed.preferences.preference_particles"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> SPEED = CommonPreferences.scale("speed", Text.key("adapt.stealthspeed.preferences.preference_speed"));

  private static final double EPSILON = 1.0E-6;
  private static final String SLOT_SNEAK = "sneak";
  private static final String SLOT_STEP = "step";
  private static final double VANILLA_SNEAK_FRACTION = 0.3D;
  private static final double VANILLA_SNEAK_ATTRIBUTE_MAX = 1.0D;
  public static final double MAX_SNEAK_SCALAR = (VANILLA_SNEAK_ATTRIBUTE_MAX / VANILLA_SNEAK_FRACTION) - 1.0D;
  private final Map<UUID, RuntimeState> states;

  public StealthSpeed() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.SNEAKING_SPEED), PlatformNeed.partialAttribute(Attributes.STEP_HEIGHT, "no step assist"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("mushroom_stew"));
    setInterval(getConfig().setInterval);
    states = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_boots"))
        .key("challenge_stealth_speed_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_stealth_speed_5k", "stealth.speed.blocks-sneak-sprinted", 5000, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, STEP_UP, STEP_DOWN, PARTICLES, SPEED);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    clearAndRemoveState(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getSpeed(getLevelPercent(level)), 0), 1);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    clearAndRemoveState(e.player());
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    clearAndRemoveState(e.player());
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef player = e.player();
    if (e.isSneaking()) {
      startSession(player);
      return;
    }

    RuntimeState state = states.get(player.uuid());
    if (state != null && !isCrawlingOnLand(player)) {
      clearAndRemoveState(player);
    }
  }

  @Handle
  public void on(PlayerMoveEvent e) {
    PlayerRef player = e.player();
    if (!player.isSneaking() && !isCrawlingOnLand(player)) {
      return;
    }

    if (states.containsKey(player.uuid())) {
      return;
    }

    if (hasActiveAdaptation(player)) {
      startSession(player);
    }
  }

  @Override
  public void unregister() {
    super.unregister();
    for (RuntimeState state : states.values()) {
      state.refreshScheduled.set(false);
    }
    states.clear();
  }

  private void startSession(PlayerRef player) {
    UUID playerId = player.uuid();
    RuntimeState state = states.computeIfAbsent(playerId, key -> new RuntimeState());
    if (state.refreshScheduled.compareAndSet(false, true)
        && !AdaptApi.scheduler().runEntity(player, () -> refreshSession(player, state), 1)) {
      clearAndRemoveState(player);
    }
  }

  private void refreshSession(PlayerRef p, RuntimeState state) {
    UUID playerId = p.uuid();
    if (states.get(playerId) != state || !p.isOnline()) {
      state.refreshScheduled.set(false);
      return;
    }

    boolean crawling = isCrawlingOnLand(p);
    if ((!p.isSneaking() && !crawling) || !hasActiveAdaptation(p)) {
      clearAndRemoveState(p);
      return;
    }

    long now = System.currentTimeMillis();
    long statIntervalMs = Math.max(50L, getConfig().statIntervalMs);

    if (!isEligible(p, crawling)) {
      clearBoost(p, state);
    } else {
      double levelFactor = getLevelPercent(p);
      applyBoost(p, state, computeSneakScalar(p, levelFactor, crawling), now);
      applyAutoStepDown(p, state, now);

      if (isMovingHorizontally(p, getConfig().movementVelocityThreshold)) {
        if (getConfig().showSoulParticles && preferenceEnabled(p, PARTICLES) && (ThreadLocalRandom.current().nextDouble() < getConfig().soulParticleChance)) {
          fx(p.location().add(0, getConfig().soulParticleYOffset, 0), FxPriority.TRAIL)
              .particle(crawling ? Particles.ASH : Particles.SOUL, 1, 0, 0, 0, 0.12D, 0);
        }

        if (now - state.lastStatMillis >= statIntervalMs) {
          addStat(p, "stealth.speed.blocks-sneak-sprinted", 1);
          state.lastStatMillis = now;
        }
      }
    }

    int delayTicks = Math.max(1, (int) Math.ceil(Math.max(50L, getConfig().setInterval) / 50.0D));
    if (!AdaptApi.scheduler().runEntity(p, () -> refreshSession(p, state), delayTicks)) {
      clearAndRemoveState(p);
    }
  }

  private void applyBoost(PlayerRef p, RuntimeState state, double sneakScalar, long now) {
    sneakScalar *= preference(p, SPEED).multiplier();
    AdaptAttributeService attributes = AdaptAttributeService.get();
    boolean starting = !state.boosting;
    if (starting) {
      state.boosting = true;

      long cooldown = Math.max(0, getConfig().activationSoundCooldownMs);
      if (cooldown <= 0 || now - state.lastSoundMillis >= cooldown) {
        Vec3 back = p.location().direction().withY(0).multiply(-1);
        fx(p.location().add(0, 0.15D, 0), FxPriority.TRANSITION)
            .particle(Particles.SOUL, 6, back.x() * 0.3D, 0.1D, back.z() * 0.3D, 0.15D, 0.02D)
            .particle(Particles.CLOUD, 4, 0, 0.1D, 0, 0.2D, 0.01D)
            .chord(Sounds.PARTICLE_SOUL_ESCAPE, getConfig().activationSoundVolume, getConfig().activationSoundPitch, Sounds.ITEM_TRIDENT_RIPTIDE_1, 0.3F, 1.4F);
        state.lastSoundMillis = now;
      }
    }

    if (starting || Math.abs(sneakScalar - state.appliedSneakScalar) > 0.0001D) {
      attributes.apply(p, getId().toString(), SLOT_SNEAK, Attributes.SNEAKING_SPEED, sneakScalar, AttributeOperation.ADD_MULTIPLIED_TOTAL);
      state.appliedSneakScalar = sneakScalar;
    }

    boolean wantStepHeight = getConfig().enableAutoStep && getConfig().enableAutoStepUp && preferenceEnabled(p, STEP_UP);
    if (wantStepHeight && !state.stepHeightApplied) {
      attributes.apply(p, getId().toString(), SLOT_STEP, Attributes.STEP_HEIGHT, Math.max(0, getConfig().stepHeightBonus), AttributeOperation.ADD_VALUE);
      state.stepHeightApplied = true;
    } else if (!wantStepHeight && state.stepHeightApplied) {
      attributes.remove(p, getId().toString(), SLOT_STEP, Attributes.STEP_HEIGHT);
      state.stepHeightApplied = false;
    }
  }

  private void clearBoost(PlayerRef p, RuntimeState state) {
    if (!state.boosting) {
      return;
    }

    state.boosting = false;
    state.appliedSneakScalar = 0.0D;
    state.stepHeightApplied = false;
    AdaptAttributeService.get().removeAll(p, getId().toString());
  }

  private void clearAndRemoveState(PlayerRef p) {
    if (p == null) {
      return;
    }

    RuntimeState state = states.remove(p.uuid());
    if (state == null) {
      return;
    }

    state.refreshScheduled.set(false);
    clearBoost(p, state);
  }

  private boolean isEligible(PlayerRef p, boolean crawlingOnLand) {
    if (!p.isSneaking() && !crawlingOnLand) {
      return false;
    }

    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    if (p.isDead() || p.vehicle() != null || p.isFlying() || p.isGliding()) {
      return false;
    }

    if ((p.isSwimming() || p.isInWater()) && !crawlingOnLand && !getConfig().allowWhileInWater) {
      return false;
    }

    return !getConfig().requireGrounded || p.isOnGround();
  }

  private boolean isCrawlingOnLand(PlayerRef p) {
    if (p.height() > getConfig().crawlHeightMax) {
      return false;
    }

    return !p.world().blockAt(p.eyeLocation()).isLiquid() && !p.world().blockAt(p.location()).isLiquid();
  }

  private double computeSneakScalar(PlayerRef p, double levelFactor, boolean crawling) {
    double bonus = sneakSpeedBonus(levelFactor, getConfig().maxSpeedBonus, crawling, getConfig().crawlBonusMultiplier);
    return sneakSpeedScalar(p.walkSpeed(), getConfig().baselineWalkSpeed, getConfig().minWalkSpeed, getConfig().maxWalkSpeed, bonus);
  }

  static double sneakSpeedBonus(double levelFactor, double maxSpeedBonus, boolean crawling, double crawlBonusMultiplier) {
    double bonus = Math.max(0, levelFactor * maxSpeedBonus);
    return crawling ? bonus * Math.max(0, crawlBonusMultiplier) : bonus;
  }

  static double sneakSpeedScalar(float currentWalkSpeed, float fallbackWalkSpeed, float minWalkSpeed, float maxWalkSpeed, double bonus) {
    float min = Math.max(-1f, minWalkSpeed);
    float max = Math.min(1f, Math.max(min, maxWalkSpeed));
    float base = Math.max(min, Math.min(max, currentWalkSpeed));
    if (Math.abs(base) < 0.0001f) {
      base = Math.max(min, Math.min(max, fallbackWalkSpeed));
    }

    if (base <= 0f) {
      return 0.0D;
    }

    double target = Math.max(min, Math.min(max, base + Math.max(0, bonus)));
    return Math.min(MAX_SNEAK_SCALAR, Math.max(0, (target - base) / base));
  }

  private void applyAutoStepDown(PlayerRef p, RuntimeState state, long now) {
    if (!getConfig().enableAutoStep || !getConfig().enableAutoStepDown || !preferenceEnabled(p, STEP_DOWN) || !p.isOnGround()) {
      return;
    }

    long cooldown = Math.max(0, getConfig().autoStepCooldownMs);
    if (cooldown > 0 && now - state.lastStepMillis < cooldown) {
      return;
    }

    Vec3 direction = resolveAutoStepDirection(p);
    if (direction.lengthSquared() <= EPSILON) {
      return;
    }

    double probe = Math.max(0.1, getConfig().autoStepProbeDistance);
    Loc front = p.location().add(direction.multiply(probe));

    tryStepDown(p, state, front, direction);
  }

  /** The horizontal direction the held movement keys point, relative to where the player looks; zero when none. */
  private static Vec3 inputDirection(PlayerRef p, PlayerInput input) {
    Vec3 look = p.location().direction().withY(0);
    if (look.lengthSquared() <= EPSILON) {
      return Vec3.ZERO;
    }
    look = look.normalize();
    Vec3 right = new Vec3(-look.z(), 0, look.x());
    Vec3 direction = Vec3.ZERO;
    if (input.forward()) {
      direction = direction.add(look);
    }
    if (input.backward()) {
      direction = direction.subtract(look);
    }
    if (input.right()) {
      direction = direction.add(right);
    }
    if (input.left()) {
      direction = direction.subtract(right);
    }
    return direction.lengthSquared() <= EPSILON ? direction : direction.normalize();
  }

  private Vec3 resolveAutoStepDirection(PlayerRef p) {
    if (getConfig().autoStepUseInput) {
      PlayerInput input = Integrations.input(p).orElse(null);
      if (input != null) {
        if (input.forward() || input.backward() || input.left() || input.right()) {
          Vec3 inputDirection = inputDirection(p, input);
          if (inputDirection.lengthSquared() > EPSILON) {
            return inputDirection;
          }
        }
      }
    }

    Vec3 movement = new Vec3(p.movement().x(), 0, p.movement().z());
    double velocityThreshold = Math.max(0, getConfig().autoStepVelocityThreshold);
    if (movement.lengthSquared() <= velocityThreshold * velocityThreshold) {
      return Vec3.ZERO;
    }

    return movement.normalize();
  }

  private boolean tryStepDown(PlayerRef p, RuntimeState state, Loc front, Vec3 direction) {
    if (!isPassable(front, 0)) {
      return false;
    }

    if (!isPassable(front, -1)) {
      return false;
    }

    if (!isSolid(front, -2)) {
      return false;
    }

    Loc destination = p.location()
        .add(direction.multiply(Math.max(0.05, getConfig().autoStepForwardPush)))
        .add(0, -1, 0);
    if (!isDestinationSafe(p, destination, true)) {
      return false;
    }

    if (!state.stepInFlight.compareAndSet(false, true)) {
      return false;
    }
    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      state.stepInFlight.set(false);
      AdaptApi.log().error("Stealth Speed could not start auto-step teleport for " + p.uuid() + ".", null);
      AdaptApi.log().error("StealthSpeed failed", error);
      return false;
    }
    if (teleport == null) {
      state.stepInFlight.set(false);
      return false;
    }
    teleport.whenComplete((success, failure) ->
        completeAutoStepTeleport(p, state, destination, success, failure));
    return true;
  }

  private void completeAutoStepTeleport(PlayerRef p, RuntimeState state, Loc destination,
                                        Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptApi.log().error("Stealth Speed auto-step teleport failed for " + p.uuid() + ".", null);
      AdaptApi.log().error("StealthSpeed failed", failure);
    }
    if (!successfulAutoStepTeleport(success, failure)) {
      state.stepInFlight.set(false);
      return;
    }
    if (!AdaptApi.scheduler().runEntity(p, () -> finishAutoStepOwned(p, state, destination))) {
      state.stepInFlight.set(false);
      AdaptApi.log().warn("Stealth Speed completed auto-step teleport but could not schedule its completion for "
          + p.uuid() + ".");
    }
  }

  private void finishAutoStepOwned(PlayerRef p, RuntimeState state, Loc destination) {
    try {
      if (states.get(p.uuid()) != state || !p.isOnline() || !isRuntimeRegistered()) {
        return;
      }
      p.setFallDistance(0);
      state.lastStepMillis = System.currentTimeMillis();
      stepFx(destination);
    } finally {
      state.stepInFlight.set(false);
    }
  }

  static boolean successfulAutoStepTeleport(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private void stepFx(Loc destination) {
    fx(destination, FxPriority.TRAIL)
        .particle(Particles.CLOUD, 2, 0, 0.05D, 0, 0.08D, 0.01D)
        .sound(Sounds.BLOCK_WOOL_STEP, 0.2F, 1.2F);
  }

  private boolean isSolid(Loc base, int yOffset) {
    BlockRef block = base.world().blockAt(base.add(0, yOffset, 0));
    return block.isSolid() && !block.isLiquid() && !block.isPassable();
  }

  private boolean isPassable(Loc base, int yOffset) {
    BlockRef block = base.world().blockAt(base.add(0, yOffset, 0));
    return block.isPassable() && !block.isLiquid();
  }

  private boolean isDestinationSafe(PlayerRef p, Loc destination, boolean requireFloor) {
    if (!isPassable(destination, 0)) {
      return false;
    }

    if (requiresDoubleHeadroom(p) && !isPassable(destination, 1)) {
      return false;
    }

    if (requireFloor && !isSolid(destination, -1)) {
      return false;
    }

    return true;
  }

  private boolean requiresDoubleHeadroom(PlayerRef p) {
    return p.boundingBox().height() >= Math.max(0.5, getConfig().doubleHeadroomHeightThreshold);
  }

  private boolean isMovingHorizontally(PlayerRef p, double threshold) {
    Vec3 horizontal = new Vec3(p.velocity().x(), 0, p.velocity().z());
    double t = Math.max(0, threshold);
    return horizontal.lengthSquared() > t * t;
  }

  private double getSpeed(double factor) {
    double bonus = sneakSpeedBonus(factor, getConfig().maxSpeedBonus, false, 0.0D);
    return sneakSpeedScalar(getConfig().baselineWalkSpeed, getConfig().baselineWalkSpeed, getConfig().minWalkSpeed, getConfig().maxWalkSpeed, bonus);
  }

  private static class RuntimeState {
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();
    private final AtomicBoolean stepInFlight = new AtomicBoolean();
    private boolean boosting;
    private double appliedSneakScalar;
    private boolean stepHeightApplied;
    private long lastSoundMillis;
    private long lastStatMillis;
    private long lastStepMillis;
  }

  @ConfigDescription("Gain speed while sneaking, up to the vanilla sneak-speed cap of full walk speed.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Reference walk speed used to convert the speed bonus into a sneak-speed multiplier when the player's live walk speed is zero.", impact = "Usually keep this at vanilla default unless another plugin changes baseline speeds globally.")
    float baselineWalkSpeed = 0.2f;
    @ConfigDoc(value = "Maximum additional walk-speed-equivalent bonus granted at max level. The default lands max level exactly on the vanilla sneak-speed cap (sneaking at full walk speed).", impact = "Higher values make stealth speed more noticeable, but the vanilla sneaking-speed clamp caps the effective boost at full walk speed.")
    double maxSpeedBonus = 0.4666666666666667;
    @ConfigDoc(value = "Multiplier applied to bonus speed while crawling on land.", impact = "Higher values make crawling keep pace with sneaking.")
    double crawlBonusMultiplier = 1.15;
    @ConfigDoc(value = "Minimum walk-speed-equivalent clamp used when computing the sneak-speed multiplier.", impact = "Keep near default to avoid unexpected slowdowns from conflicting systems.")
    float minWalkSpeed = -1f;
    @ConfigDoc(value = "Maximum walk-speed-equivalent clamp used when computing the sneak-speed multiplier.", impact = "Lower values cap how strong the sneak-speed boost can get.")
    float maxWalkSpeed = 1f;
    @ConfigDoc(value = "Enables automatic vertical stepping while stealth speed is active.", impact = "Helps smooth sneaking over one-block terrain changes.")
    boolean enableAutoStep = true;
    @ConfigDoc(value = "Grants extra step height for one-block ledges while stealth speed is active.", impact = "Reduces sneak interruption when encountering small ledges.")
    boolean enableAutoStepUp = true;
    @ConfigDoc(value = "Extra step height granted while stealth speed is active and step-up is enabled.", impact = "0.4 raises the vanilla 0.6 step height to a full block.")
    double stepHeightBonus = 0.4;
    @ConfigDoc(value = "Allows stepping down one block while moving.", impact = "Only steps down when the drop is exactly one block.")
    boolean enableAutoStepDown = true;
    @ConfigDoc(value = "Forward probe distance for auto-step-down checks.", impact = "Higher values detect ledges earlier but can feel more aggressive.")
    double autoStepProbeDistance = 0.45;
    @ConfigDoc(value = "Horizontal push applied during each auto-step-down teleport.", impact = "Higher values move farther off the next block and reduce repeat stepping in place.")
    double autoStepForwardPush = 0.36;
    @ConfigDoc(value = "Uses direct movement input for auto-step-down direction when available.", impact = "Helps auto-step trigger while pressing toward ledges, even when velocity is near zero.")
    boolean autoStepUseInput = true;
    @ConfigDoc(value = "Minimum horizontal velocity required before auto-step-down runs.", impact = "Higher values avoid accidental stepping while nearly idle.")
    double autoStepVelocityThreshold = 0.01;
    @ConfigDoc(value = "Minimum delay between auto-step-down teleports.", impact = "Higher values reduce repeated stepping in tight terrain.")
    long autoStepCooldownMs = 90;
    @ConfigDoc(value = "Bounding-box height above which two-block headroom is required for step-down destinations.", impact = "Lower values are stricter; higher values allow sneaking/crawling to step in tighter spaces.")
    double doubleHeadroomHeightThreshold = 1.7;
    @ConfigDoc(value = "Maximum bounding-box height counted as crawling on land.", impact = "Higher values make crawl detection more permissive.")
    double crawlHeightMax = 0.61;
    @ConfigDoc(value = "Requires players to be grounded for stealth speed to run.", impact = "True avoids midair acceleration and keeps behavior stable.")
    boolean requireGrounded = true;
    @ConfigDoc(value = "Allows stealth speed to run while the player is in water.", impact = "False prevents stealth from overriding seaborne-style underwater movement effects.")
    boolean allowWhileInWater = false;
    @ConfigDoc(value = "Minimum horizontal velocity used to count the player as moving for FX/stat tracking.", impact = "Higher values reduce effects while nearly stationary.")
    double movementVelocityThreshold = 0.005;
    @ConfigDoc(value = "Shows a subtle soul particle near the player's feet while stealth speed is active.", impact = "Visual feedback visible only to the boosted player.")
    boolean showSoulParticles = true;
    @ConfigDoc(value = "Chance per tick to spawn a soul particle while moving.", impact = "Higher values make the effect denser; lower values are subtler.")
    double soulParticleChance = 0.3;
    @ConfigDoc(value = "Vertical offset for the soul particle effect.", impact = "Small positive values keep particles around floor level.")
    double soulParticleYOffset = 0.02;
    @ConfigDoc(value = "Activation sound volume heard by the boosted player.", impact = "Higher values are louder; lower values are subtler.")
    float activationSoundVolume = 1.6f;
    @ConfigDoc(value = "Activation sound pitch heard by the boosted player.", impact = "Higher values raise tone; lower values deepen it.")
    float activationSoundPitch = 0.9f;
    @ConfigDoc(value = "Minimum time between activation sounds.", impact = "Higher values reduce audio spam when repeatedly starting/stopping.")
    long activationSoundCooldownMs = 250;
    @ConfigDoc(value = "Minimum time between progression stat increments while moving with stealth speed.", impact = "Controls how quickly the sneak-speed progression stat accumulates.")
    long statIntervalMs = 200;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
      maxLevel = 3;
      setInterval = 50;
    }
  }
}
