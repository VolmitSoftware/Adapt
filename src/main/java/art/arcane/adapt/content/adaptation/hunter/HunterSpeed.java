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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.math.VelocitySpeed;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class HunterSpeed extends SimpleAdaptation<HunterSpeed.Config> {
  private final Map<UUID, SpeedBurst> speedBursts = playerState();

  public HunterSpeed() {
    super("hunter-speed");
    registerConfiguration(Config.class);
    setIcon(Material.SUGAR);
    setInterval(getConfig().setInterval);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SUGAR)
        .key("challenge_hunter_speed_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_hunter_speed_200", "hunter.speed.activations", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("hunter.speed.lore1"));
    statLore(v, level, 2);
    statLore(v, C.RED, "- ", (5 + level), 3);
    statLore(v, C.GRAY, "* ", level, 4);
    statLore(v, C.GRAY, "* ", level, 5);
    v.addLore(C.GRAY + "- " + level + C.RED + " " + Localizer.dLocalize("hunter.penalty.lore1"));

  }


  @EventHandler
  public void on(EntityDamageEvent e) {
    if (e.getEntity() instanceof org.bukkit.entity.Player p && isAdaptableDamageCause(e) && hasActiveAdaptation(p)) {
      if (AdaptConfig.get().isPreventHunterSkillsWhenHungerApplied() && p.hasPotionEffect(PotionEffectType.HUNGER)) {
        return;
      }

      if (!getConfig().useConsumable) {
        if (p.getFoodLevel() == 0) {
          if (getConfig().poisonPenalty) {
            addPotionStacks(p, PotionEffectType.POISON, getConfig().basePoisonFromLevel - getLevel(p), getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
          }

        } else {
          addPotionStacks(p, PotionEffectType.HUNGER, getConfig().baseHungerFromLevel - getLevel(p), getConfig().baseHungerDuration * getLevel(p), getConfig().stackHungerPenalty);
          grantSpeedBurst(p, getLevel(p), getConfig().baseEffectbyLevel * getLevel(p), getConfig().stackBuff);
          addStat(p, "hunter.speed.activations", 1);
        }
      } else {
        if (getConfig().consumable != null && Material.getMaterial(getConfig().consumable) != null) {
          Material mat = Material.getMaterial(getConfig().consumable);
          if (mat != null && p.getInventory().contains(mat)) {
            p.getInventory().removeItem(new ItemStack(mat, 1));
            grantSpeedBurst(p, getLevel(p), getConfig().baseEffectbyLevel * getLevel(p), getConfig().stackBuff);
            addStat(p, "hunter.speed.activations", 1);
          } else {
            if (getConfig().poisonPenalty) {
              addPotionStacks(p, PotionEffectType.POISON, getConfig().basePoisonFromLevel - getLevel(p), getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
            }
          }
        }
      }
    }
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    speedBursts.remove(e.getEntity().getUniqueId());
  }

  private void grantSpeedBurst(org.bukkit.entity.Player p, int amplifier, int durationTicks, boolean overlap) {
    if (durationTicks <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    long now = System.currentTimeMillis();
    long durationMs = Math.max(50L, durationTicks * 50L);
    SpeedBurst current = speedBursts.get(id);
    if (current != null && current.expiresAt > now) {
      if (!overlap) {
        return;
      }

      current.expiresAt += durationMs;
      current.amplifier = Math.max(current.amplifier, amplifier);
      return;
    }

    speedBursts.put(id, new SpeedBurst(now + durationMs, amplifier));
    Vector back = p.getLocation().getDirection().multiply(-1);
    fx(p.getLocation().add(0, 0.5D, 0), FxPriority.TRANSITION)
        .trail(Particle.CLOUD, back.getX(), back.getY(), back.getZ(), 1.4D, 8)
        .chord(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 1.5F, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.4F);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      org.bukkit.entity.Player p = adaptPlayer.getPlayer();
      SpeedBurst burst = speedBursts.get(p.getUniqueId());
      if (burst == null) {
        continue;
      }

      if (burst.expiresAt <= now) {
        invalidateBurst(p, burst, false);
        speedBursts.remove(p.getUniqueId());
        fx(p.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 3, 0.2D)
            .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.2F, 0.6F);
        continue;
      }

      if (!isVelocityEligible(p)) {
        invalidateBurst(p, burst, true);
        continue;
      }

      VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(p, getConfig().fallbackInputVelocityThresholdSquared());
      if (!input.hasHorizontal()) {
        brakeBurst(p, burst);
        continue;
      }

      applyBurst(p, burst, input);
    }
  }

  private void applyBurst(org.bukkit.entity.Player p, SpeedBurst burst, VelocitySpeed.InputSnapshot input) {
    Vector desiredDirection = VelocitySpeed.resolveHorizontalDirection(p, input);
    if (desiredDirection.lengthSquared() <= VelocitySpeed.EPSILON) {
      brakeBurst(p, burst);
      return;
    }

    double targetSpeed = Math.min(getConfig().maxHorizontalSpeed,
        Math.max(0, getConfig().baseHorizontalSpeed * VelocitySpeed.speedAmplifierScalar(burst.amplifier)));
    Vector velocity = p.getVelocity();
    Vector currentHorizontal = VelocitySpeed.horizontalOnly(velocity);
    Vector targetHorizontal = desiredDirection.multiply(targetSpeed);
    Vector nextHorizontal = VelocitySpeed.moveTowards(currentHorizontal, targetHorizontal, Math.max(0, getConfig().accelPerTick));
    nextHorizontal = VelocitySpeed.clampHorizontal(nextHorizontal, getConfig().maxHorizontalSpeed);
    VelocitySpeed.setHorizontalVelocity(p, nextHorizontal);
    burst.boosting = true;
    if ((burst.trailTick++ % 3) == 0) {
      fx(p.getLocation().add(0, 0.1D, 0), FxPriority.TRAIL)
          .trail(Particle.CLOUD, -nextHorizontal.getX(), 0, -nextHorizontal.getZ(), 0.8D, 3);
    }
  }

  private void invalidateBurst(org.bukkit.entity.Player p, SpeedBurst burst, boolean invalidState) {
    if (!burst.boosting) {
      return;
    }

    if (invalidState && getConfig().hardStopOnInvalidState) {
      VelocitySpeed.hardStopHorizontal(p);
    }

    burst.boosting = false;
  }

  private void brakeBurst(org.bukkit.entity.Player p, SpeedBurst burst) {
    if (!burst.boosting) {
      return;
    }

    Vector velocity = p.getVelocity();
    Vector currentHorizontal = VelocitySpeed.horizontalOnly(velocity);
    double stopThreshold = Math.max(0, getConfig().stopThreshold);
    if (currentHorizontal.lengthSquared() <= stopThreshold * stopThreshold) {
      VelocitySpeed.hardStopHorizontal(p);
      burst.boosting = false;
      brakeScuff(p);
      return;
    }

    Vector nextHorizontal = VelocitySpeed.moveTowards(currentHorizontal, new Vector(), Math.max(0, getConfig().brakePerTick));
    if (nextHorizontal.lengthSquared() <= stopThreshold * stopThreshold) {
      VelocitySpeed.hardStopHorizontal(p);
      burst.boosting = false;
      brakeScuff(p);
      return;
    }

    VelocitySpeed.setHorizontalVelocity(p, nextHorizontal);
  }

  private void brakeScuff(org.bukkit.entity.Player p) {
    fx(p.getLocation(), FxPriority.TRAIL)
        .burst(Particle.CLOUD, 4, 0.2D)
        .sound(Sound.BLOCK_SAND_STEP, 0.3F, 0.8F);
  }

  private boolean isVelocityEligible(org.bukkit.entity.Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isDead() && !p.isFlying() && !p.isGliding() && !p.isSwimming() && p.getVehicle() == null;
  }

  private static class SpeedBurst {
    private long expiresAt;
    private int amplifier;
    private boolean boosting;
    private int trailTick;

    private SpeedBurst(long expiresAt, int amplifier) {
      this.expiresAt = expiresAt;
      this.amplifier = amplifier;
    }

  }

  @ConfigDescription("Gain speed when struck, at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Tick interval (ms) used to update velocity speed bursts.", impact = "Lower values feel more responsive but run updates more frequently.")
    long setInterval = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Use Consumable for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useConsumable = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Poison Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean poisonPenalty = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Hunger Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackHungerPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Poison Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackPoisonPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Buff for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackBuff = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Effectby Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseEffectbyLevel = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger Duration for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerDuration = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger From Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerFromLevel = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Poison From Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int basePoisonFromLevel = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base horizontal speed used for hunter bursts before amplifier scaling.", impact = "Higher values increase movement speed while a burst is active.")
    double baseHorizontalSpeed = 0.13;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum horizontal speed this adaptation can force.", impact = "Acts as a hard cap to prevent runaway momentum.")
    double maxHorizontalSpeed = 0.32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How fast velocity accelerates toward the burst target per tick.", impact = "Higher values accelerate faster; lower values feel smoother.")
    double accelPerTick = 0.045;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How fast velocity decays when movement input is released.", impact = "Higher values reduce carry momentum more aggressively.")
    double brakePerTick = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal velocity threshold considered fully stopped.", impact = "Higher values stop sooner; lower values preserve tiny momentum longer.")
    double stopThreshold = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "If true, burst velocity is force-cleared when entering invalid states.", impact = "Prevents retained speed when state changes skip expected flow.")
    boolean hardStopOnInvalidState = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fallback movement threshold used when direct input API is unavailable.", impact = "Only used on runtimes without Player input access.")
    double fallbackInputVelocityThreshold = 0.0008;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Consumable for the Hunter Speed adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
    String consumable = "ROTTEN_FLESH";

    public Config() {
      costFactor = 0.4;
      initialCost = 8;
    }

    double fallbackInputVelocityThresholdSquared() {
      double threshold = Math.max(0, fallbackInputVelocityThreshold);
      return threshold * threshold;
    }
  }
}
