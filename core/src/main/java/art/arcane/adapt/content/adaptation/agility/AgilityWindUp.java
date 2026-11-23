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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDismountEvent;
import art.arcane.adapt.platform.event.entity.EntityMountEvent;
import art.arcane.adapt.platform.event.entity.EntityToggleGlideEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleFlightEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgilityWindUp extends SimpleAdaptation<AgilityWindUp.Config> {
  public static final PlayerPreference<CommonPreferences.Scale> SPEED = CommonPreferences.scale("speed", AgilityMessages.AGILITYWINDUP_PREFERENCE_SPEED);

  private static final String SLOT_WINDUP = "windup";
  private static final double DEFAULT_WALK_SPEED = 0.2D;
  private final Map<UUID, RuntimeState> states = playerState();

  public AgilityWindUp() {
    super("agility-wind-up");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("powered_rail"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("powered_rail"))
        .key("challenge_agility_wind_up_10min")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_agility_wind_up_10min", "agility.wind-up.max-speed-ticks", 12000, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SPEED);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    clearAndRemoveState(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getWindupSpeed(getLevelPercent(level)), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1), 2);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    clearAndRemoveState(e.player());
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    clearAndRemoveState(e.player());
  }

  @ReceiveCancelledEvents
  @Handle
  public void on(EntityMountEvent event) {
    if (!(event.entity() instanceof PlayerRef p)) {
      return;
    }

    clearAndRemoveState(p);
  }

  @ReceiveCancelledEvents
  @Handle
  public void on(EntityDismountEvent event) {
    if (!(event.entity() instanceof PlayerRef p)) {
      return;
    }

    clearAndRemoveState(p);
  }

  private double getWindupTicks(double factor) {
    return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
  }

  private double getWindupSpeed(double factor) {
    return getConfig().windupSpeedBase + (factor * getConfig().windupSpeedLevelMultiplier);
  }

  @Handle
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    updatePlayer(p, p.isSprinting());
  }

  @Handle
  public void on(PlayerToggleSprintEvent e) {
    if (!e.isSprinting()) {
      clearAndRemoveState(e.player());
      return;
    }
    updatePlayer(e.player(), true);
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    if (e.isSneaking()) {
      clearAndRemoveState(e.player());
    }
  }

  @Handle
  public void on(PlayerToggleFlightEvent e) {
    if (e.isFlying()) {
      clearAndRemoveState(e.player());
    }
  }

  @Handle
  public void on(EntityToggleGlideEvent e) {
    if (e.entity() instanceof PlayerRef p && e.gliding()) {
      clearAndRemoveState(p);
    }
  }

  @Handle
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.newGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      clearAndRemoveState(e.player());
    }
  }

  private void updatePlayer(PlayerRef p, boolean sprinting) {
    if (p == null || !p.isOnline()) {
      return;
    }

    if (!sprinting && states.isEmpty()) {
      return;
    }

    UUID id = p.uuid();
    RuntimeState state = states.get(id);
    if (state == null && !sprinting) {
      return;
    }
    if (!sprinting || !isWindupEligible(p) || !hasActiveAdaptation(p)) {
      if (state != null) {
        clearBoost(p, state);
        states.remove(id, state);
      }
      return;
    }

    double factor = getLevelPercent(p);
    if (factor <= 0) {
      if (state != null) {
        clearBoost(p, state);
        states.remove(id, state);
      }
      return;
    }

    if (state == null) {
      state = new RuntimeState();
      states.put(id, state);
    }
    long now = System.currentTimeMillis();
    double elapsedTicks = elapsedTicks(state.lastUpdateAt, now);
    state.lastUpdateAt = now;
    if (elapsedTicks <= 0D) {
      return;
    }
    state.runningTicks += elapsedTicks;

    double ticksToMax = Math.max(1D, getWindupTicks(factor));
    double progress = Math.min(M.lerpInverse(0, ticksToMax, state.runningTicks), 1);
    double speedIncrease = M.lerp(0, getWindupSpeed(factor), progress);
    applyBoost(p, state, speedIncrease, elapsedTicks);
    emitChargeFeedback(p, state, progress);

    if (progress >= 1.0 && isMovingHorizontally(p, getConfig().movementVelocityThreshold)) {
      addStat(p, "agility.wind-up.max-speed-ticks", elapsedTicks);
      if (M.r(0.25)) {
        Vec3 back = p.velocity();
        fx(p, FxPriority.TRAIL).trail(Particles.SOUL_FIRE_FLAME, -back.x(), 0.05D, -back.z(), 0.8D, 1);
      }
    }
  }

  private void emitChargeFeedback(PlayerRef p, RuntimeState state, double progress) {
    int bracket = (int) Math.floor(progress / 0.25D);
    if (bracket <= state.lastBracket) {
      return;
    }

    state.lastBracket = bracket;
    if (progress >= 1.0D) {
      if (state.ignited) {
        return;
      }

      state.ignited = true;
      Vec3 back = p.velocity();
      fx(p, FxPriority.GAMEPLAY)
          .trail(Particles.FLAME, -back.x(), 0.1D, -back.z(), 1.2D, 6)
          .chord(Sounds.ENTITY_BLAZE_SHOOT, 0.3F, 0.7F, Sounds.BLOCK_FIRE_AMBIENT, 0.4F, 1.4F);
      return;
    }

    if (bracket < 1) {
      return;
    }

    fx(p.location(), FxPriority.GAMEPLAY)
        .ring(Particles.CLOUD, 1.1D - (bracket * 0.23D), 8, 0.1D)
        .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.4F, 0.6F + (bracket * 0.2F));
  }

  private void applyBoost(PlayerRef p, RuntimeState state, double speedIncrease, double elapsedTicks) {
    if (!state.boosting) {
      state.boosting = true;
      state.currentBonus = 0D;
    }

    double target = targetBonus(speedIncrease, getConfig().walkSpeedBonusScalar, getConfig().maxWalkSpeed) * preference(p, SPEED).multiplier();
    float smoothing = elapsedSmoothing(getConfig().walkSpeedLerpPerTick, elapsedTicks);
    double next = smoothedBonus(state.currentBonus, target, smoothing);
    if (Math.abs(state.currentBonus - next) > 0.0005D) {
      state.currentBonus = next;
      AdaptAttributeService.get().apply(p, getName(), SLOT_WINDUP, Attributes.MOVEMENT_SPEED, next, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    }
  }

  private void clearBoost(PlayerRef p, RuntimeState state) {
    if (!state.boosting) {
      return;
    }

    boolean wasCharged = state.lastBracket >= 1;
    state.boosting = false;
    state.lastBracket = 0;
    state.ignited = false;
    state.currentBonus = 0D;
    AdaptAttributeService.get().remove(p, getName(), SLOT_WINDUP, Attributes.MOVEMENT_SPEED);

    if (wasCharged) {
      fx(p.location(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.1D)
          .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.5F);
    }
  }

  private void clearAndRemoveState(PlayerRef p) {
    if (p == null) {
      return;
    }

    UUID id = p.uuid();
    RuntimeState state = states.remove(id);
    if (state != null) {
      clearBoost(p, state);
    }
  }

  private boolean isMovingHorizontally(PlayerRef p, double velocityThreshold) {
    Vec3 velocity = p.velocity();
    double threshold = Math.max(0D, velocityThreshold);
    return (velocity.x() * velocity.x()) + (velocity.z() * velocity.z()) > (threshold * threshold);
  }

  private boolean isWindupEligible(PlayerRef p) {
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isDead()
        && !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && !p.isSneaking()
        && p.vehicle() == null;
  }

  static double targetBonus(double speedIncrease, double bonusScalar, double maxWalkSpeed) {
    double target = Math.max(0D, speedIncrease) * Math.max(0D, bonusScalar);
    double cap = Math.max(0D, (Math.max(0D, maxWalkSpeed) / DEFAULT_WALK_SPEED) - 1D);
    return Math.min(target, cap);
  }

  static double smoothedBonus(double currentBonus, double target, float smoothing) {
    double next = currentBonus + ((target - currentBonus) * smoothing);
    if (Math.abs(target - next) < 0.0025D) {
      return target;
    }
    return next;
  }

  static double elapsedTicks(long previousUpdateAt, long now) {
    if (previousUpdateAt <= 0L) {
      return 1D;
    }
    if (now <= previousUpdateAt) {
      return 0D;
    }
    return Math.min(20D, (now - previousUpdateAt) / 50D);
  }

  static float elapsedSmoothing(double perTickSmoothing, double elapsedTicks) {
    double clamped = Math.max(0D, Math.min(1D, perTickSmoothing));
    if (clamped <= 0D || elapsedTicks <= 0D) {
      return 0F;
    }
    return (float) (1D - Math.pow(1D - clamped, elapsedTicks));
  }


  @ConfigDescription("Get faster the longer you sprint.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Slowest for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupTicksSlowest = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Fastest for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupTicksFastest = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Speed Base for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupSpeedBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Speed Level Multiplier for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupSpeedLevelMultiplier = 0.225;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scales the relative movement-speed modifier gained from windup speed increase while sprinting.", impact = "Higher values produce a stronger relative speed bonus.")
    double walkSpeedBonusScalar = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Smooths the relative movement-speed modifier toward the windup target bonus each tick.", impact = "Higher values ramp faster; lower values feel softer.")
    double walkSpeedLerpPerTick = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Effective walk-speed ceiling expressed against the 0.2 vanilla base; the relative movement-speed bonus is capped at maxWalkSpeed / 0.2 - 1.", impact = "Higher values raise the relative bonus cap for faster grounded sprinting.")
    double maxWalkSpeed = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum horizontal movement speed required for max-speed stat credit.", impact = "Higher values require clearer movement before counting max-speed ticks.")
    double movementVelocityThreshold = 0.015;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  private static class RuntimeState {
    private long lastUpdateAt;
    private double runningTicks;
    private boolean boosting;
    private double currentBonus;
    private int lastBracket;
    private boolean ignited;
  }

}
