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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.events.api.ReflectiveHandler;
import art.arcane.adapt.util.reflect.events.api.entity.EntityDismountEvent;
import art.arcane.adapt.util.reflect.events.api.entity.EntityMountEvent;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class AgilityWindUp extends SimpleAdaptation<AgilityWindUp.Config> {
  private static final String SLOT_WINDUP = "windup";
  private static final double DEFAULT_WALK_SPEED = 0.2D;
  private final Map<UUID, RuntimeState> states = playerState();

  public AgilityWindUp() {
    super("agility-wind-up");
    registerConfiguration(Config.class);
    setIcon(Material.POWERED_RAIL);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.POWERED_RAIL)
        .key("challenge_agility_wind_up_10min")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_agility_wind_up_10min", "agility.wind-up.max-speed-ticks", 12000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getWindupSpeed(getLevelPercent(level)), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1), 2);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    clearAndRemoveState(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    clearAndRemoveState(e.getEntity());
  }

  @ReflectiveHandler
  public void on(EntityMountEvent event) {
    if (event.getEntity().getType() != EntityType.PLAYER) {
      return;
    }

    Player p = (Player) event.getEntity();
    clearAndRemoveState(p);
  }

  @ReflectiveHandler
  public void on(EntityDismountEvent event) {
    if (event.getEntity().getType() != EntityType.PLAYER) {
      return;
    }

    Player p = (Player) event.getEntity();
    clearAndRemoveState(p);
  }

  private double getWindupTicks(double factor) {
    return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
  }

  private double getWindupSpeed(double factor) {
    return getConfig().windupSpeedBase + (factor * getConfig().windupSpeedLevelMultiplier);
  }

  @EventHandler
  public void on(PlayerMoveEvent e) {
    updatePlayer(e.getPlayer(), e.getPlayer().isSprinting());
  }

  @EventHandler
  public void on(PlayerToggleSprintEvent e) {
    if (!e.isSprinting()) {
      clearAndRemoveState(e.getPlayer());
      return;
    }
    updatePlayer(e.getPlayer(), true);
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    if (e.isSneaking()) {
      clearAndRemoveState(e.getPlayer());
    }
  }

  @EventHandler
  public void on(PlayerToggleFlightEvent e) {
    if (e.isFlying()) {
      clearAndRemoveState(e.getPlayer());
    }
  }

  @EventHandler
  public void on(EntityToggleGlideEvent e) {
    if (e.getEntity() instanceof Player p && e.isGliding()) {
      clearAndRemoveState(p);
    }
  }

  @EventHandler
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.getNewGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      clearAndRemoveState(e.getPlayer());
    }
  }

  private void updatePlayer(Player p, boolean sprinting) {
    if (p == null || !p.isOnline()) {
      return;
    }

    UUID id = p.getUniqueId();
    RuntimeState state = states.get(id);
    if (state == null && !sprinting) {
      return;
    }
    if (!hasActiveAdaptation(p) || !isWindupEligible(p) || !sprinting) {
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
        Vector back = p.getVelocity();
        fx(p, FxPriority.TRAIL).trail(Particle.SOUL_FIRE_FLAME, -back.getX(), 0.05D, -back.getZ(), 0.8D, 1);
      }
    }
  }

  private void emitChargeFeedback(Player p, RuntimeState state, double progress) {
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
      Vector back = p.getVelocity();
      fx(p, FxPriority.GAMEPLAY)
          .trail(Particle.FLAME, -back.getX(), 0.1D, -back.getZ(), 1.2D, 6)
          .chord(Sound.ENTITY_BLAZE_SHOOT, 0.3F, 0.7F, Sound.BLOCK_FIRE_AMBIENT, 0.4F, 1.4F);
      return;
    }

    if (bracket < 1) {
      return;
    }

    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .ring(Particle.CLOUD, 1.1D - (bracket * 0.23D), 8, 0.1D)
        .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.4F, 0.6F + (bracket * 0.2F));
  }

  private void applyBoost(Player p, RuntimeState state, double speedIncrease, double elapsedTicks) {
    if (!state.boosting) {
      state.boosting = true;
      state.currentBonus = 0D;
    }

    double target = targetBonus(speedIncrease, getConfig().walkSpeedBonusScalar, getConfig().maxWalkSpeed);
    float smoothing = elapsedSmoothing(getConfig().walkSpeedLerpPerTick, elapsedTicks);
    double next = smoothedBonus(state.currentBonus, target, smoothing);
    if (Math.abs(state.currentBonus - next) > 0.0005D) {
      state.currentBonus = next;
      AdaptAttributeService.get().apply(p, getName(), SLOT_WINDUP, Attributes.MOVEMENT_SPEED, next, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }
  }

  private void clearBoost(Player p, RuntimeState state) {
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
      fx(p.getLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.1D)
          .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.5F);
    }
  }

  private void clearAndRemoveState(Player p) {
    if (p == null) {
      return;
    }

    UUID id = p.getUniqueId();
    RuntimeState state = states.remove(id);
    if (state != null) {
      clearBoost(p, state);
    }
  }

  private boolean isMovingHorizontally(Player p, double velocityThreshold) {
    Vector velocity = p.getVelocity();
    double threshold = Math.max(0D, velocityThreshold);
    return (velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ()) > (threshold * threshold);
  }

  private boolean isWindupEligible(Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isDead()
        && !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && !p.isSneaking()
        && p.getVehicle() == null;
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
      baseCost = 2;
      costFactor = 0.65;
      initialCost = 8;
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
