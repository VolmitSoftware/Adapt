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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TragoulBloodPact extends SimpleAdaptation<TragoulBloodPact.Config> {
  private static List<PotionEffectType> effectPool() {
    return List.of(
        PotionEffectType.SPEED,
        PotionEffectType.REGENERATION,
        PotionEffectType.RESISTANCE,
        PotionEffectType.FIRE_RESISTANCE,
        PotionEffectType.ABSORPTION,
        PotionEffectType.JUMP_BOOST,
        PotionEffectType.NIGHT_VISION);
  }

  private static final Color PACT_CRIMSON = Color.fromRGB(150, 0, 10);
  private final Cooldowns procCooldowns = cooldowns();
  private final Map<UUID, Boolean> lowHealthProcs = playerState();

  public TragoulBloodPact() {
    super("tragoul-blood-pact");
    registerConfiguration(Config.class);
    setIcon(Material.NETHER_WART);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.REDSTONE)
        .key("challenge_tragoul_pact_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_SWORD)
        .key("challenge_tragoul_pact_kills_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_tragoul_pact_200", "tragoul.blood-pact.health-sacrificed", 200, 400);
    registerMilestone("challenge_tragoul_pact_kills_500", "tragoul.blood-pact.empowered-kills", 500, 1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.REDSTONE)
        .key("challenge_tragoul_pact_all_in")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getProcChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.blood_pact.lore1"));
    v.addLore(C.GREEN + "+ " + Form.duration(getEffectDurationTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.blood_pact.lore2"));
    v.addLore(C.YELLOW + "* " + Form.duration(getProcCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.blood_pact.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      double settledDamage = e.getFinalDamage();
      if (level <= 0 || !isTriggeringDamage(settledDamage, getMinTriggerDamage())) {
        return;
      }

      if (!procCooldowns.isReady(p.getUniqueId(), getProcCooldownMillis(level))) {
        return;
      }

      if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
        return;
      }

      procCooldowns.mark(p.getUniqueId());
      addStat(p, "tragoul.blood-pact.health-sacrificed", (int) settledDamage);
      if (p.getHealth() - settledDamage <= 6.0) {
        lowHealthProcs.put(p.getUniqueId(), true);
      }
      applyRandomBuffs(p, level, settledDamage);
      playPactProc(p);
      xp(p, getConfig().xpPerProc);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent dmgEvent) {
      if (dmgEvent.getDamager() instanceof Player p) {
        withAdaptedPlayer(p, () -> {
          if (p.hasPotionEffect(PotionEffectType.ABSORPTION) || p.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            addStat(p, "tragoul.blood-pact.empowered-kills", 1);
            if (lowHealthProcs.getOrDefault(p.getUniqueId(), false) && grantOnce(p, "challenge_tragoul_pact_all_in")) {
              fx(p.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
                  .burst(Particles.TOTEM, 12, 0.4)
                  .sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5F, 1.5F);
            }
          }
        });
      }
    }
  }

  private void applyRandomBuffs(Player p, int level, double takenDamage) {
    int count = getBuffCount(level);
    if (takenDamage >= (getMinTriggerDamage() * 1.6)) {
      count++;
    }
    if (ThreadLocalRandom.current().nextDouble() <= getBonusBuffChance(level)) {
      count++;
    }

    List<PotionEffectType> pool = new ArrayList<>(effectPool());
    Collections.shuffle(pool);
    count = Math.min(count, pool.size());

    int duration = getEffectDurationTicks(level);
    for (int i = 0; i < count; i++) {
      PotionEffectType type = pool.get(i);
      int amplifier = getEffectAmplifier(type, level);
      int d = type == PotionEffectType.ABSORPTION ? Math.max(40, duration - 20) : duration;
      if (type == PotionEffectType.SPEED) {
        grantSpeedBoost(p, amplifier, d);
        continue;
      }
      if (type == PotionEffectType.JUMP_BOOST) {
        grantJumpBoost(p, amplifier, d);
        continue;
      }
      p.addPotionEffect(new PotionEffect(type, d, amplifier, false, true, true), true);
    }
  }

  private void grantSpeedBoost(Player p, int amplifier, int durationTicks) {
    if (durationTicks <= 0) {
      return;
    }
    AdaptAttributeService.get().applyTimed(p, getName(), "speed", Attributes.MOVEMENT_SPEED, speedBonus(amplifier), AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
    fx(p, FxPriority.TRAIL)
        .dustBurst(PACT_CRIMSON, 4, 0.3, 1.0F)
        .sound(Sound.PARTICLE_SOUL_ESCAPE, 0.3F, 1.5F);
  }

  private void grantJumpBoost(Player p, int amplifier, int durationTicks) {
    if (durationTicks <= 0) {
      return;
    }
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(p, getName(), "jump", Attributes.JUMP_STRENGTH, jumpStrengthBonus(amplifier), AttributeModifier.Operation.ADD_NUMBER, durationTicks);
    attributes.applyTimed(p, getName(), "fall", Attributes.SAFE_FALL_DISTANCE, safeFallBonus(amplifier), AttributeModifier.Operation.ADD_NUMBER, durationTicks);
  }

  static double speedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  static double jumpStrengthBonus(int amplifier) {
    return 0.1D * (amplifier + 1);
  }

  static double safeFallBonus(int amplifier) {
    return amplifier + 1.0D;
  }

  static boolean isTriggeringDamage(double settledDamage, double minimumDamage) {
    return Double.isFinite(settledDamage) && Double.isFinite(minimumDamage)
        && settledDamage >= Math.max(0D, minimumDamage);
  }

  private void playPactProc(Player p) {
    timeline(p)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          Color spiral = Color.fromRGB((int) (150 + (80 * progress)), (int) (180 * progress), (int) (10 + (30 * progress)));
          fx.dustHelix(spiral, 0.7, 2.2, 6, progress * Math.PI * 2.0, 1.0F);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6F, 1.0F, Sound.BLOCK_BEACON_ACTIVATE, 0.4F, 1.6F, Sound.ENTITY_WITHER_SPAWN, 0.15F, 2.0F);
          } else if (tick == 3 || tick == 6) {
            fx.sound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.4F, tick == 3 ? 1.5F : 1.8F);
          } else if (tick == 9) {
            fx.burst(Particles.TOTEM, 8, 0.4);
          }
        })
        .start();
  }

  private double getProcChance(int level) {
    return Math.min(getConfig().maxProcChance,
        Math.max(0, getConfig().procChanceBase + (getLevelPercent(level) * getConfig().procChanceFactor)));
  }

  private long getProcCooldownMillis(int level) {
    return Math.max(500L, (long) Math.round(getConfig().procCooldownMillisBase - (getLevelPercent(level) * getConfig().procCooldownMillisFactor)));
  }

  private int getEffectDurationTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().effectDurationTicksBase + (getLevelPercent(level) * getConfig().effectDurationTicksFactor)));
  }

  private int getBuffCount(int level) {
    return Math.max(1, (int) Math.round(getConfig().buffCountBase + (getLevelPercent(level) * getConfig().buffCountFactor)));
  }

  private double getBonusBuffChance(int level) {
    return Math.min(0.9, Math.max(0, getConfig().bonusBuffChanceBase + (getLevelPercent(level) * getConfig().bonusBuffChanceFactor)));
  }

  private int getEffectAmplifier(PotionEffectType type, int level) {
    double progress = getLevelPercent(level);
    if (type == PotionEffectType.ABSORPTION || type == PotionEffectType.RESISTANCE || type == PotionEffectType.REGENERATION) {
      return progress >= 0.85 ? 1 : 0;
    }
    return progress >= 0.7 ? 1 : 0;
  }

  private double getMinTriggerDamage() {
    return Math.max(1, getConfig().minDamageTriggerHearts * 2D);
  }

  @ConfigDescription("Losing at least 2 hearts after damage mitigation can trigger temporary beneficial effects.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Damage Trigger Hearts for the Tragoul Blood Pact adaptation.", impact = "Minimum final health loss in hearts required before the proc roll happens.")
    double minDamageTriggerHearts = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double procChanceBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double procChanceFactor = 0.38;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Proc Chance for the Tragoul Blood Pact adaptation.", impact = "Caps chance at the requested maximum.")
    double maxProcChance = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Proc Cooldown Millis Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double procCooldownMillisBase = 18000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Proc Cooldown Millis Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double procCooldownMillisFactor = 12000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Duration Ticks Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double effectDurationTicksBase = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Duration Ticks Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double effectDurationTicksFactor = 150;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Buff Count Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double buffCountBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Buff Count Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double buffCountFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Buff Chance Base for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusBuffChanceBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Buff Chance Factor for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusBuffChanceFactor = 0.34;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Per Proc for the Tragoul Blood Pact adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerProc = 24;

    public Config() {
      costFactor = 0.62;
      initialCost = 4;
    }
  }
}
