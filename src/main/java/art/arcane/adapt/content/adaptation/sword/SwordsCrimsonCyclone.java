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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import de.slikey.effectlib.effect.BleedEffect;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class SwordsCrimsonCyclone extends SimpleAdaptation<SwordsCrimsonCyclone.Config> {
  private static final Color CRIMSON = Color.fromRGB(0x9E1414);

  public SwordsCrimsonCyclone() {
    super("sword-crimson-cyclone");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_SWORD);
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_cyclone_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_swords_cyclone_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_swords_cyclone_500", "swords.crimson-cyclone.mobs-hit", 500, 400);
    registerMilestone("challenge_swords_cyclone_5k", "swords.crimson-cyclone.mobs-hit", 5000, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_SWORD)
        .key("challenge_swords_cyclone_6")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, Form.f(getBaseDamage(level), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity primaryTarget = combat.target();
    ItemStack hand = combat.mainHand();
    if (!isCritTrigger(p)) {
      return;
    }

    int level = combat.level();
    int hungerCost = getHungerCost(level);
    if (p.getFoodLevel() < hungerCost) {
      return;
    }

    if (!applyDurabilityCost(hand, getDurabilityCost(level))) {
      return;
    }

    int hits = 0;
    int sparks = 0;
    double radius = getRadius(level);
    double damage = getBaseDamage(level);
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
    p.setCooldown(hand.getType(), getCooldownTicks(level));

    e.setDamage(e.getDamage() + damage);
    applyBleed(primaryTarget, level);
    crimsonSpark(primaryTarget);
    sparks++;
    hits++;

    for (Entity entity : primaryTarget.getWorld().getNearbyEntities(primaryTarget.getLocation(), radius, radius, radius)) {
      if (!(entity instanceof LivingEntity target)) {
        continue;
      }

      if (target == p || target == primaryTarget) {
        continue;
      }

      if (!canDamageTarget(p, target)) {
        continue;
      }

      target.damage(damage, p);
      applyBleed(target, level);
      if (sparks < 9) {
        crimsonSpark(target);
        sparks++;
      }
      hits++;
    }

    if (hits <= 0) {
      return;
    }

    double maxRadius = radius;
    int ringPoints = Math.min(24, 10 + (level * 2));
    timeline(primaryTarget.getLocation())
        .duration(8)
        .priority(FxPriority.COMBAT)
        .cullRadius(Math.min(48D, maxRadius + 16D))
        .frame((fx, tick, progress) -> {
          double growth = tick <= 3 ? (tick + 1) / 4.0D : (8 - tick) / 4.0D;
          fx.ring(Particle.CRIMSON_SPORE, maxRadius * growth, ringPoints, 1.0D);
          if (tick == 0) {
            fx.sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1F, 0.6F);
          } else if (tick == 2) {
            fx.sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 1.2F);
          } else if (tick == 4) {
            fx.sound(Sound.ENTITY_WITHER_HURT, 0.65F, 1.45F);
          }
        })
        .start();
    xp(p, hits * getConfig().xpPerTargetHit);
    addStat(p, "swords.crimson-cyclone.mobs-hit", hits);

    if (hits >= 6 && grantOnce(p, "challenge_swords_cyclone_6")) {
      fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.TOTEM, 12, 0.4D)
          .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
          .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1F);
    }
  }

  private void crimsonSpark(LivingEntity target) {
    fx(target.getLocation().add(0, 0.8D, 0), FxPriority.COMBAT)
        .particle(Particle.CRIMSON_SPORE, 3, 0, 0, 0, 0.3D, 0.01D)
        .dustBurst(CRIMSON, 1, 0.2D, 1.0F);
  }

  private boolean isCritTrigger(Player p) {
    return p.getFallDistance() >= getConfig().minFallDistanceForCrit
        && !p.isOnGround()
        && !p.isInWater()
        && !p.isInsideVehicle()
        && !p.isClimbing();
  }

  private boolean applyDurabilityCost(ItemStack hand, int durabilityCost) {
    if (!(hand.getItemMeta() instanceof Damageable damageable) || hand.getType().getMaxDurability() <= 0) {
      return true;
    }

    int max = hand.getType().getMaxDurability();
    int next = damageable.getDamage() + durabilityCost;
    if (next >= max) {
      return false;
    }

    damageable.setDamage(next);
    hand.setItemMeta(damageable);
    return true;
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getBaseDamage(int level) {
    return getConfig().baseDamage + (getLevelPercent(level) * getConfig().damageFactor);
  }

  private void applyBleed(LivingEntity target, int level) {
    BleedEffect bleed = new DamagingBleedEffect(Adapt.instance.adaptEffectManager, getBleedDamagePerProc(level), target);
    bleed.setEntity(target);
    bleed.material = getConfig().showBleedParticles ? Material.CRIMSON_ROOTS : Material.VOID_AIR;
    bleed.height = -1;
    bleed.period = 5;
    bleed.hurt = false;
    bleed.iterations = Math.max(4, (int) Math.ceil(getBleedTicks(level) / 5D));
    bleed.start();
  }

  private int getBleedTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().bleedTicksBase + (getLevelPercent(level) * getConfig().bleedTicksFactor)));
  }

  private double getBleedDamagePerProc(int level) {
    return Math.max(0.01, getConfig().bleedDamagePerProcBase + (getLevelPercent(level) * getConfig().bleedDamagePerProcFactor));
  }

  private int getHungerCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().hungerCostBase - (getLevelPercent(level) * getConfig().hungerCostFactor)));
  }

  private int getDurabilityCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().durabilityCostBase - (getLevelPercent(level) * getConfig().durabilityCostFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Land a sword crit while falling to unleash a bleeding crimson cyclone around your target.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Bleed Particles for the Swords Crimson Cyclone adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showBleedParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 2.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedTicksFactor = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Damage Per Proc Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedDamagePerProcBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Damage Per Proc Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedDamagePerProcFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 320;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 160;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Fall Distance For Crit for the Swords Crimson Cyclone adaptation.", impact = "Minimum fall distance required to trigger the cyclone on hit.")
    float minFallDistanceForCrit = 0.08f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Hit for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetHit = 10;

    public Config() {
      baseCost = 5;
      costFactor = 0.76;
      initialCost = 5;
    }
  }
}
