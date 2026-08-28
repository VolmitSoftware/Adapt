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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class UnarmedGlassCannon extends SimpleAdaptation<UnarmedGlassCannon.Config> {
  public UnarmedGlassCannon() {
    super("unarmed-glass-cannon");
    registerConfiguration(Config.class);
    setIcon(Material.POINTED_DRIPSTONE);
    setInterval(4544);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLASS)
        .key("challenge_unarmed_glass_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.GLASS_PANE)
            .key("challenge_unarmed_glass_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_glass_100", "unarmed.glass-cannon.naked-kills", 100, 300);
    registerMilestone("challenge_unarmed_glass_500", "unarmed.glass-cannon.naked-kills", 500, 1000);
  }


  @Override
  public void addStats(int level, Element v) {
    statLore(v, getConfig().maxDamageFactor + (level * getConfig().maxDamagePerLevelMultiplier), 1);
    statLore(v, Form.f(level * getConfig().perLevelBonusMultiplier), 2);
  }

  @EventHandler
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();

    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    double armor = getArmorValue(p);
    double damage = e.getDamage();
    int level = attack.level();
    double flatBonus = level * getConfig().perLevelBonusMultiplier;

    if (armor == 0) {
      e.setDamage(Math.max(damage, (damage * (getConfig().maxDamageFactor + (level * getConfig().maxDamagePerLevelMultiplier))) + flatBonus));
      fx(e.getEntity(), FxPriority.COMBAT)
          .particle(Particles.BLOCK_CRACK, 14, 0, 1.0D, 0, 0.3D, 0.05D, Material.GLASS.createBlockData())
          .chord(Sound.BLOCK_GLASS_BREAK, 0.8F, 1.2F, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 1.5F);
    } else {
      e.setDamage(Math.max(damage, (damage - (damage * armor)) + flatBonus));
      fx(e.getEntity(), FxPriority.COMBAT)
          .particle(Particle.CRIT, 2, 0, 1.0D, 0, 0.15D, 0.0D);
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
        && !isTool(p.getInventory().getItemInMainHand())
        && !isTool(p.getInventory().getItemInOffHand())
        && getArmorValue(p) == 0) {
      addStat(p, "unarmed.glass-cannon.naked-kills", 1);
      fx(victim, FxPriority.COMBAT)
          .particle(Particles.BLOCK_CRACK, 10, 0, 1.0D, 0, 0.35D, 0.05D, Material.GLASS.createBlockData())
          .chord(Sound.BLOCK_GLASS_BREAK, 0.9F, 0.9F, Sound.ENTITY_ITEM_BREAK, 0.5F, 1.6F);
      timeline(victim.getLocation())
          .duration(5)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> fx.dustRing(Color.WHITE, 0.3D + (1.7D * progress), 16, 1.0F))
          .start();
    }
  }


  @ConfigDescription("Bonus unarmed damage the lower your armor value is.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Per Level Bonus Multiplier for the Unarmed Glass Cannon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double perLevelBonusMultiplier = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Damage Factor for the Unarmed Glass Cannon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDamageFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Damage Per Level Multiplier for the Unarmed Glass Cannon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDamagePerLevelMultiplier = 0.15;

    public Config() {
      baseCost = 3;
      costFactor = 0.425;
      maxLevel = 7;
      initialCost = 6;
    }
  }

}
