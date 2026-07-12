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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class NetherMagmaSkin extends SimpleAdaptation<NetherMagmaSkin.Config> {
  public NetherMagmaSkin() {
    super("nether-magma-skin");
    registerConfiguration(Config.class);
    setIcon(Material.MAGMA_CREAM);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.MAGMA_CREAM)
        .key("challenge_nether_magma_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.MAGMA_BLOCK)
            .key("challenge_nether_magma_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_nether_magma_100", "nether.magma-skin.attackers-ignited", 100, 300);
    registerMilestone("challenge_nether_magma_2500", "nether.magma-skin.attackers-ignited", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.duration(getReflectFireTicks(level) * 50D, 1), 1);
    statLore(v, C.RED, "+ ", Form.f(getBonusDamage(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onDefend(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.getFireTicks() <= 0) {
        return;
      }
      if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
          && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
        return;
      }
      if (!(e.getDamager() instanceof LivingEntity attacker) || attacker == p) {
        return;
      }

      int level = getActiveLevel(p);
      int ticks = getReflectFireTicks(level);
      J.runEntity(attacker, () -> attacker.setFireTicks(Math.max(attacker.getFireTicks(), ticks)));
      addStat(p, "nether.magma-skin.attackers-ignited", 1);
      xp(p, getConfig().xpOnReflect);
      fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.COMBAT)
          .burst(Particle.FLAME, 6, 0.35D)
          .particle(Particle.LAVA, 2, 0D, 0.3D, 0D, 0.2D, 0.0D)
          .chord(Sound.ENTITY_BLAZE_SHOOT, 0.4F, 1.3F, Sound.BLOCK_FIRE_AMBIENT, 0.3F, 1.0F);
    });
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onAttack(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.getFireTicks() <= 0) {
        return;
      }
      if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
          && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
        return;
      }
      if (!canDamageTarget(p, target)) {
        return;
      }

      int level = getActiveLevel(p);
      double bonus = getBonusDamage(level);
      if (bonus <= 0D) {
        return;
      }

      e.setDamage(e.getDamage() + bonus);
      int fire = getBonusFireTicks(level);
      J.runEntity(target, () -> target.setFireTicks(Math.max(target.getFireTicks(), fire)));
      xp(p, bonus * getConfig().xpPerBonusDamage);
      fx(target.getLocation().add(0D, 0.8D, 0D), FxPriority.COMBAT)
          .burst(Particle.FLAME, 5, 0.3D)
          .particle(Particle.SOUL_FIRE_FLAME, 2, 0D, 0.2D, 0D, 0.2D, 0.0D)
          .sound(Sound.ENTITY_BLAZE_HURT, 0.35F, 1.5F);
    });
  }

  private int getReflectFireTicks(int level) {
    return reflectFireTicks(getLevelPercent(level), getConfig().reflectFireTicksBase, getConfig().reflectFireTicksFactor);
  }

  private double getBonusDamage(int level) {
    return bonusDamage(getLevelPercent(level), getConfig().bonusDamageBase, getConfig().bonusDamageFactor);
  }

  private int getBonusFireTicks(int level) {
    return reflectFireTicks(getLevelPercent(level), getConfig().bonusFireTicksBase, getConfig().bonusFireTicksFactor);
  }

  static int reflectFireTicks(double levelPercent, double base, double factor) {
    return Math.max(0, (int) Math.round(base + (levelPercent * factor)));
  }

  static double bonusDamage(double levelPercent, double base, double factor) {
    return base + (levelPercent * factor);
  }


  @ConfigDescription("While burning, melee attackers catch fire and your own strikes deal bonus fire damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Fire Ticks Base for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectFireTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Fire Ticks Factor for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectFireTicksFactor = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Base for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageBase = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Factor for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageFactor = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Fire Ticks Base for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusFireTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Fire Ticks Factor for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusFireTicksFactor = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Reflect for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnReflect = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Damage for the Nether Magma Skin adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDamage = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
