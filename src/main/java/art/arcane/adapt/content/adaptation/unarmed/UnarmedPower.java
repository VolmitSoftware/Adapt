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
import org.bukkit.event.entity.EntityDeathEvent;

public class UnarmedPower extends SimpleAdaptation<UnarmedPower.Config> {
  public UnarmedPower() {
    super("unarmed-power");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_INGOT);
    setInterval(4444);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_power_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_power_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_power_500", "unarmed.power.unarmed-kills", 500, 400);
    registerMilestone("challenge_unarmed_power_5k", "unarmed.power.unarmed-kills", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getUnarmedDamage(level), 0), 1);
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
    double factor = getLevelPercent(attack.level());

    if (factor <= 0) {
      return;
    }
    e.setDamage(e.getDamage() * (1 + getUnarmedDamage(attack.level())));
    xp(p, 0.321 * factor * e.getDamage(), "unarmed-hit");
    if (factor >= 0.5) {
      fx(e.getEntity(), FxPriority.COMBAT)
          .particle(Particle.CRIT, 3, 0, 1.0D, 0, 0.1D, 0.0D)
          .sound(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.35F, (float) (0.9D + (factor * 0.3D)));
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
        && !isTool(p.getInventory().getItemInOffHand())) {
      addStat(p, "unarmed.power.unarmed-kills", 1);
      fx(victim, FxPriority.COMBAT)
          .particle(Particle.SOUL, 5, 0, 0.4D, 0, 0.2D, 0.03D)
          .sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.3F);
    }
  }

  private double getUnarmedDamage(int level) {
    return getLevelPercent(level) * getConfig().damageFactor;
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Improved base unarmed damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Unarmed Power adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 2.57;

    public Config() {
      baseCost = 3;
      costFactor = 0.425;
      maxLevel = 7;
      initialCost = 6;
    }
  }
}
