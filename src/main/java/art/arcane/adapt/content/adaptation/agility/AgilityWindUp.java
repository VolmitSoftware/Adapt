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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.events.api.ReflectiveHandler;
import art.arcane.adapt.util.reflect.events.api.entity.EntityDismountEvent;
import art.arcane.adapt.util.reflect.events.api.entity.EntityMountEvent;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AgilityWindUp extends SimpleAdaptation<AgilityWindUp.Config> {
  private final Map<UUID, Integer> ticksRunning;
  private final Map<UUID, RuntimeState> states;

  public AgilityWindUp() {
    super("agility-wind-up");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("agility.wind_up.description"));
    setDisplayName(Localizer.dLocalize("agility.wind_up.name"));
    setIcon(Material.POWERED_RAIL);
    setBaseCost(getConfig().baseCost);
    setCostFactor(getConfig().costFactor);
    setInitialCost(getConfig().initialCost);
    setInterval(50);
    ticksRunning = new ConcurrentHashMap<>();
    states = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.POWERED_RAIL)
        .key("challenge_agility_wind_up_10min")
        .title(Localizer.dLocalize("advancement.challenge_agility_wind_up_10min.title"))
        .description(Localizer.dLocalize("advancement.challenge_agility_wind_up_10min.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ACTIVATOR_RAIL)
            .key("challenge_agility_wind_up_2hr")
            .title(Localizer.dLocalize("advancement.challenge_agility_wind_up_2hr.title"))
            .description(Localizer.dLocalize("advancement.challenge_agility_wind_up_2hr.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_wind_up_10min", "agility.wind-up.max-speed-ticks", 12000, 400);
    registerMilestone("challenge_agility_wind_up_2hr", "agility.wind-up.max-speed-ticks", 144000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getWindupSpeed(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("agility.wind_up.lore1"));
    v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("agility.wind_up.lore2"));
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
    UUID id = p.getUniqueId();
    ticksRunning.remove(id);
    RuntimeState state = states.get(id);
    if (state != null) {
      clearBoost(p, state);
    }
  }

  @ReflectiveHandler
  public void on(EntityDismountEvent event) {
    if (event.getEntity().getType() != EntityType.PLAYER) {
      return;
    }

    Player p = (Player) event.getEntity();
    UUID id = p.getUniqueId();
    ticksRunning.remove(id);
    RuntimeState state = states.get(id);
    if (state != null) {
      clearBoost(p, state);
    }
  }

  private double getWindupTicks(double factor) {
    return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
  }

  private double getWindupSpeed(double factor) {
    return getConfig().windupSpeedBase + (factor * getConfig().windupSpeedLevelMultiplier);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }
      withPlayerThread(p, () -> updatePlayer(p));
    }
  }

  private void updatePlayer(Player p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    UUID id = p.getUniqueId();
    RuntimeState state = states.computeIfAbsent(id, key -> new RuntimeState());
    if (!hasActiveAdaptation(p) || !isWindupEligible(p) || !p.isSprinting()) {
      ticksRunning.remove(id);
      clearBoost(p, state);
      return;
    }

    double factor = getLevelPercent(p);
    if (factor <= 0) {
      ticksRunning.remove(id);
      clearBoost(p, state);
      return;
    }

    ticksRunning.compute(id, (k, v) -> v == null ? 1 : v + 1);
    int tr = ticksRunning.getOrDefault(id, 0);
    if (tr <= 0) {
      return;
    }

    double ticksToMax = Math.max(1D, getWindupTicks(factor));
    double progress = Math.min(M.lerpInverse(0, ticksToMax, tr), 1);
    double speedIncrease = M.lerp(0, getWindupSpeed(factor), progress);
    applyBoost(p, state, speedIncrease);

    if (areParticlesEnabled()) {
      if (M.r(0.2 * progress)) {
        p.getWorld().spawnParticle(Particle.LAVA, p.getLocation(), 1);
      }

      if (M.r(0.25 * progress)) {
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 1, 0, 0, 0, 0);
      }
    }

    if (progress >= 1.0 && isMovingHorizontally(p, getConfig().movementVelocityThreshold)) {
      getPlayer(p).getData().addStat("agility.wind-up.max-speed-ticks", 1);
    }
  }

  private void applyBoost(Player p, RuntimeState state, double speedIncrease) {
    if (!state.boosting) {
      state.boosting = true;
      state.originalWalkSpeed = p.getWalkSpeed();
    }

    float baseWalkSpeed = clampWalkSpeed(state.originalWalkSpeed);
    double bonus = Math.max(0D, speedIncrease) * Math.max(0D, getConfig().walkSpeedBonusScalar);
    float target = clampWalkSpeed((float) (baseWalkSpeed * (1D + bonus)));
    float configuredMaxWalkSpeed = clampWalkSpeed((float) Math.max(0D, getConfig().maxWalkSpeed));
    float maxWalkSpeed = Math.max(baseWalkSpeed, configuredMaxWalkSpeed);
    if (target > maxWalkSpeed) {
      target = maxWalkSpeed;
    }

    float smoothing = (float) Math.max(0D, Math.min(1D, getConfig().walkSpeedLerpPerTick));
    float current = p.getWalkSpeed();
    float next = current + ((target - current) * smoothing);
    if (Math.abs(target - next) < 0.0005f) {
      next = target;
    }

    if (Math.abs(current - next) > 0.0001f) {
      p.setWalkSpeed(next);
    }
  }

  private void clearBoost(Player p, RuntimeState state) {
    if (!state.boosting) {
      return;
    }

    state.boosting = false;
    float restore = clampWalkSpeed(state.originalWalkSpeed);
    float current = p.getWalkSpeed();
    if (Math.abs(current - restore) > 0.0001f) {
      p.setWalkSpeed(restore);
    }
  }

  private void clearAndRemoveState(Player p) {
    if (p == null) {
      return;
    }

    UUID id = p.getUniqueId();
    ticksRunning.remove(id);
    RuntimeState state = states.remove(id);
    if (state != null) {
      clearBoost(p, state);
    }
  }

  private boolean isMovingHorizontally(Player p, double velocityThreshold) {
    Vector movement = new Vector(p.getVelocity().getX(), 0, p.getVelocity().getZ());
    double threshold = Math.max(0D, velocityThreshold);
    return movement.lengthSquared() > (threshold * threshold);
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

  private float clampWalkSpeed(float speed) {
    if (speed < 0f) {
      return 0f;
    }
    if (speed > 1f) {
      return 1f;
    }
    return speed;
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }


  @NoArgsConstructor
  @ConfigDescription("Get faster the longer you sprint.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Agility Wind Up adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Slowest for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupTicksSlowest = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Fastest for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupTicksFastest = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Speed Base for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupSpeedBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Speed Level Multiplier for the Agility Wind Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupSpeedLevelMultiplier = 0.225;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scales walk-speed gain from windup speed increase while sprinting.", impact = "Higher values produce stronger land-speed acceleration.")
    double walkSpeedBonusScalar = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Smooths walk-speed changes toward windup target each tick.", impact = "Higher values ramp faster; lower values feel softer.")
    double walkSpeedLerpPerTick = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum walk speed this adaptation can set while windup is active.", impact = "Higher values allow faster grounded sprinting before clamping.")
    double maxWalkSpeed = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum horizontal movement speed required for max-speed stat credit.", impact = "Higher values require clearer movement before counting max-speed ticks.")
    double movementVelocityThreshold = 0.015;
  }

  private static class RuntimeState {
    private boolean boosting;
    private float originalWalkSpeed;
  }

}
