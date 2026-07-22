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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.UnarmedMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

public class UnarmedSuckerPunch extends SimpleAdaptation<UnarmedSuckerPunch.Config> {
  public UnarmedSuckerPunch() {
    super("unarmed-sucker-punch");
    registerConfiguration(Config.class);
    setIcon(Material.OBSIDIAN);
    setInterval(4944);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_sucker_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_unarmed_sucker_500", "unarmed.sucker-punch.sucker-punches", 500, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND)
        .key("challenge_unarmed_knockout")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_unarmed_knockout", "unarmed.sucker-punch.one-punch-kills", 50, 1000);
  }


  @Override
  public void addStats(int level, Element v) {
    double f = getLevelPercent(level);
    double d = getDamage(f);
    statLore(v, Form.pc(d, 0), 1);
    v.addLore(C.GRAY + AdaptLanguage.text(UnarmedMessages.SUCKER_PUNCH_LORE2));
  }

  private double getDamage(double f) {
    return getConfig().baseDamage + (f * getConfig().damageFactor);
  }

  @EventHandler
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();
    if (p.getInventory().getItemInMainHand().getType() != Material.AIR) {
      return;
    }
    double factor = getLevelPercent(attack.level());

    if (!p.isSprinting()) {
      return;
    }

    if (factor <= 0) {
      return;
    }

    e.setDamage(e.getDamage() * (1 + getDamage(factor)));
    Location impact = e.getEntity().getLocation().add(0, 1.0D, 0);
    Vector look = p.getLocation().getDirection();
    fx(impact, FxPriority.COMBAT)
        .particle(Particle.CRIT, 12, 0, 0, 0, 0.2D, 0.1D)
        .particle(Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
        .trail(Particle.CRIT, look.getX(), look.getY(), look.getZ(), 1.2D, 6)
        .chord(Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.8F, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 1.4F, Sound.BLOCK_BASALT_BREAK, 0.9F, 0.55F);
    xp(p, 6.221 * e.getDamage(), "sucker-punch");
    addStat(p, "unarmed.sucker-punch.sucker-punches", 1);
    if (e.getDamage() > 5) {
      xp(p, 0.42 * e.getDamage(), "bonus-damage");
      timeline(e.getEntity().getLocation().add(0, 1.0D, 0))
          .duration(4)
          .priority(FxPriority.COMBAT)
          .frame((fx, tick, progress) -> fx.ring(Particle.ELECTRIC_SPARK, 1.2D - (0.9D * progress), 10, 0))
          .start();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity() instanceof LivingEntity victim)) {
      return;
    }
    if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
        && dmg.getDamager() instanceof Player p
        && hasActiveAdaptation(p)
        && p.isSprinting()
        && p.getInventory().getItemInMainHand().getType() == Material.AIR) {
      if (victim.getMaxHealth() <= dmg.getFinalDamage()) {
        addStat(p, "unarmed.sucker-punch.one-punch-kills", 1);
        Location ko = victim.getLocation().add(0, 1.0D, 0);
        fx(ko, FxPriority.COMBAT)
            .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
            .burst(Particle.CRIT, 24, 0.4D)
            .chord(Sound.ITEM_TOTEM_USE, 0.5F, 1.9F, Sound.ENTITY_GENERIC_EXPLODE, 0.4F, 1.6F, Sound.BLOCK_ANVIL_LAND, 0.35F, 1.8F);
        timeline(victim.getLocation())
            .duration(6)
            .priority(FxPriority.TRANSITION)
            .frame((fx, tick, progress) -> fx.dustRing(Color.WHITE, 2.5D * progress, 24, 1.1F))
            .start();
      }
    }
  }


  @ConfigDescription("Sprint punches with an empty main hand deal extra damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Unarmed Sucker Punch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Unarmed Sucker Punch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 0.55;

    public Config() {
      baseCost = 2;
      costFactor = 0.225;
      initialCost = 4;
    }
  }
}
