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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SeabornePressureDiver extends SimpleAdaptation<SeabornePressureDiver.Config> {
  private final Cooldowns xpCooldowns = cooldowns();
  private final Cooldowns absorbFx = cooldowns();
  private final Map<UUID, Boolean> deep = playerState();
  private final Map<UUID, Boolean> deepTier = playerState();

  public SeabornePressureDiver() {
    super("seaborne-pressure-diver");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.pressure_diver");
    setIcon(Material.NAUTILUS_SHELL);
    setInterval(20);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_seaborne_pressure_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_seaborne_pressure_1k", "seaborne.pressure-diver.deep-blocks-mined", 1000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDepthThreshold(level), 1), 1);
    statLore(v, Form.pc(getDamageReduction(level), 0), 2);
    statLore(v, Form.pc(getFatigueTrimChance(level), 0), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !p.isInWater() || !isDeepEnough(p, level)) {
        return;
      }

      e.setDamage(e.getDamage() * (1D - getDamageReduction(level)));
      if (!absorbFx.isReady(p.getUniqueId(), 400L)) {
        return;
      }

      absorbFx.mark(p.getUniqueId());
      fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.COMBAT)
          .ring(Particle.BUBBLE, 0.7D, 10, 0.0D)
          .dustRing(0.7D, 8, 1.0F)
          .chord(Sound.ITEM_SHIELD_BLOCK, 0.4F, 0.8F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.3F, 0.9F);
    });
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }

      withPlayerThread(p, () -> {
        if (!p.isOnline()) {
          return;
        }

        int level = getActiveLevel(p);
        UUID id = p.getUniqueId();
        if (level <= 0 || !p.isInWater()) {
          clearDepthState(id);
          return;
        }

        double depth = p.getWorld().getSeaLevel() - p.getEyeLocation().getY();
        if (depth < getDepthThreshold(level)) {
          clearDepthState(id);
          return;
        }

        boolean inDeepTier = depth >= getDeepThreshold(level);
        applyDepthBuffs(p, level, inDeepTier ? 1 : 0);
        awardDepthXp(p);
        addStat(p, "seaborne.pressure-diver.deep-blocks-mined", 1);

        if (!deep.getOrDefault(id, false)) {
          deep.put(id, true);
          emitPressureSeal(p);
        }

        boolean wasTier = deepTier.getOrDefault(id, false);
        if (inDeepTier && !wasTier) {
          deepTier.put(id, true);
          emitDeepTier(p);
        } else if (!inDeepTier && wasTier) {
          deepTier.put(id, false);
        }
      });
    }
  }

  private void clearDepthState(UUID id) {
    if (deep.remove(id) != null) {
      deepTier.remove(id);
    }
  }

  private void applyDepthBuffs(Player p, int level, int resistanceAmp) {
    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, getConfig().effectTicks, resistanceAmp, false, false, true), true);
    p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, getConfig().effectTicks, 0, false, false, true), true);

    PotionEffect fatigue = p.getPotionEffect(PotionEffectType.MINING_FATIGUE);
    if (fatigue == null) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getFatigueTrimChance(level)) {
      return;
    }

    int reducedAmp = Math.max(0, fatigue.getAmplifier() - getFatigueTrimAmount(level));
    p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
        Math.max(20, Math.min(fatigue.getDuration(), getConfig().fatigueReplaceTicks)),
        reducedAmp,
        false,
        true,
        true), true);
    fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.AMBIENT)
        .particle(Particle.CRIT, 4, 0D, 0.3D, 0D, 0.3D, 0.05D)
        .dustBurst(3, 0.25D, 0.8F)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.6F);
  }

  private void awardDepthXp(Player p) {
    UUID id = p.getUniqueId();
    if (!xpCooldowns.isReady(id, getConfig().xpPulseCooldownMillis)) {
      return;
    }

    xpCooldowns.mark(id);
    xp(p, getConfig().xpPerDepthPulse);
    fx(p.getLocation(), FxPriority.AMBIENT).sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2F, 1.4F);
  }

  private void emitPressureSeal(Player p) {
    timeline(p)
        .duration(12)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          double radius = 1.2D - (1.0D * progress);
          f.ring(Particle.BUBBLE, radius, 14, 0.4D);
          f.dustRing(radius, 8, 1.0F);
          if (tick == 0) {
            f.chord(Sound.BLOCK_CONDUIT_ACTIVATE, 0.5F, 1.4F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.3F, 0.8F);
          } else if (tick == 6) {
            f.sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.35F, 0.7F);
          }
        })
        .start();
  }

  private void emitDeepTier(Player p) {
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .dome(Particle.SOUL, 1.0D, 6)
        .particle(Particle.GLOW, 4, 0D, 0.5D, 0D, 0.4D, 0.01D)
        .sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.4F, 0.9F);
  }

  private boolean isDeepEnough(Player p, int level) {
    double seaLevel = p.getWorld().getSeaLevel();
    double depth = seaLevel - p.getEyeLocation().getY();
    return depth >= getDepthThreshold(level);
  }

  private double getDepthThreshold(int level) {
    return Math.max(2, getConfig().depthThresholdBase - (getLevelPercent(level) * getConfig().depthThresholdFactor));
  }

  private double getDeepThreshold(int level) {
    return Math.max(4, getConfig().deepThresholdBase - (getLevelPercent(level) * getConfig().deepThresholdFactor));
  }

  private double getDamageReduction(int level) {
    return Math.min(getConfig().maxDamageReduction, getConfig().damageReductionBase + (getLevelPercent(level) * getConfig().damageReductionFactor));
  }

  private double getFatigueTrimChance(int level) {
    return Math.min(1.0, getConfig().fatigueTrimChanceBase + (getLevelPercent(level) * getConfig().fatigueTrimChanceFactor));
  }

  private int getFatigueTrimAmount(int level) {
    return Math.max(1, (int) Math.round(getConfig().fatigueTrimAmountBase + (getLevelPercent(level) * getConfig().fatigueTrimAmountFactor)));
  }

  @ConfigDescription("Gain depth scaling protection underwater and partially counter mining fatigue in deep ocean play.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Depth Threshold Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthThresholdBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Depth Threshold Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthThresholdFactor = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Deep Threshold Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double deepThresholdBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Deep Threshold Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double deepThresholdFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Reduction Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageReductionBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Reduction Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageReductionFactor = 0.26;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Maximum Damage Reduction for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDamageReduction = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Chance Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fatigueTrimChanceBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Chance Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fatigueTrimChanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Amount Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fatigueTrimAmountBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Amount Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fatigueTrimAmountFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Ticks for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int effectTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Replace Ticks for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int fatigueReplaceTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Per Depth Pulse for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDepthPulse = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Pulse Cooldown Millis for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long xpPulseCooldownMillis = 3000;

    public Config() {
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
