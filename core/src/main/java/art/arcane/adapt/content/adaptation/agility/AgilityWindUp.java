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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.AgilityMessages;
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

public class AgilityWindUp extends SimpleAdaptation<AgilityWindUp.Config> {
  public static final PlayerPreference<CommonPreferences.Scale> SPEED = CommonPreferences.scale("speed", AgilityMessages.AGILITYWINDUP_PREFERENCE_SPEED);

  private static final double DEFAULT_WALK_SPEED = 0.2D;
  private final AgilityRunRamp runRamp = new AgilityRunRamp(this, playerState(),
      (PlayerRef player, double ticks) -> addStat(player, "agility.wind-up.max-speed-ticks", ticks));

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

    if (!sprinting && !runRamp.tracking(p.uuid())) {
      return;
    }
    if (!sprinting || !AgilityRunRamp.eligible(p, false) || !hasActiveAdaptation(p)) {
      runRamp.clear(p);
      return;
    }

    double factor = getLevelPercent(p);
    if (factor <= 0) {
      runRamp.clear(p);
      return;
    }

    double speedMultiplier = preference(p, SPEED).multiplier();
    double windupSpeed = getWindupSpeed(factor);
    runRamp.update(p, new AgilityRunRamp.Ramp(getWindupTicks(factor), true,
        (double progress) -> targetBonus(M.lerp(0, windupSpeed, progress), getConfig().walkSpeedBonusScalar,
            getConfig().maxWalkSpeed) * speedMultiplier,
        getConfig().walkSpeedLerpPerTick, getConfig().movementVelocityThreshold), System.currentTimeMillis());
  }

  private void clearAndRemoveState(PlayerRef p) {
    runRamp.clear(p);
  }

  static double targetBonus(double speedIncrease, double bonusScalar, double maxWalkSpeed) {
    double target = Math.max(0D, speedIncrease) * Math.max(0D, bonusScalar);
    double cap = Math.max(0D, (Math.max(0D, maxWalkSpeed) / DEFAULT_WALK_SPEED) - 1D);
    return Math.min(target, cap);
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


  static void fullSpeedTrailFx(Adaptation<?> owner, PlayerRef p) {
    Vec3 back = p.velocity();
    Fx.now(owner, p, FxPriority.TRAIL).trail(Particles.SOUL_FIRE_FLAME, -back.x(), 0.05D, -back.z(), 0.8D, 1);
  }

  static void igniteFx(Adaptation<?> owner, PlayerRef p) {
    Vec3 back = p.velocity();
    Fx.now(owner, p, FxPriority.GAMEPLAY)
        .trail(Particles.FLAME, -back.x(), 0.1D, -back.z(), 1.2D, 6)
        .chord(Sounds.ENTITY_BLAZE_SHOOT, 0.3F, 0.7F, Sounds.BLOCK_FIRE_AMBIENT, 0.4F, 1.4F);
  }

  static void chargeFx(Adaptation<?> owner, PlayerRef p, int bracket) {
    Fx.now(owner, p.location(), FxPriority.GAMEPLAY)
        .ring(Particles.CLOUD, 1.1D - (bracket * 0.23D), 8, 0.1D)
        .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.4F, 0.6F + (bracket * 0.2F));
  }

  static void winddownFx(Adaptation<?> owner, PlayerRef p) {
    Fx.now(owner, p.location(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.1D)
        .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.5F);
  }
}
