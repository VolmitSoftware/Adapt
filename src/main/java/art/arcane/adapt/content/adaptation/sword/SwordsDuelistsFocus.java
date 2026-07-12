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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SwordsDuelistsFocus extends SimpleAdaptation<SwordsDuelistsFocus.Config> {
  private static final Color FOCUS = Color.fromRGB(0x59C0FF);
  private final Cooldowns fxThrottle = cooldowns();

  public SwordsDuelistsFocus() {
    super("sword-duelists-focus");
    registerConfiguration(Config.class);
    setIcon(Material.SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_duelist_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_swords_duelist_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_swords_duelist_200", "swords.duelists-focus.focused-hits", 200, 400);
    registerMilestone("challenge_swords_duelist_2500", "swords.duelists-focus.focused-hits", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getBonusDamage(level), 0), 1);
    statLore(v, Form.pc(getDamageReduction(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat != null && countEngaged(combat.attacker()) == 1) {
      Player attacker = combat.attacker();
      e.setDamage(e.getDamage() * (1D + getBonusDamage(combat.level())));
      xp(attacker, getConfig().xpPerFocusedHit);
      addStat(attacker, "swords.duelists-focus.focused-hits", 1);
      focusFx(attacker, true);
    }

    if (e.getEntity() instanceof Player victim) {
      int level = getActiveLevel(victim);
      if (level > 0 && isSword(victim.getInventory().getItemInMainHand()) && countEngaged(victim) == 1) {
        e.setDamage(Math.max(0D, e.getDamage() * (1D - getDamageReduction(level))));
        focusFx(victim, false);
      }
    }
  }

  private int countEngaged(Player p) {
    double radius = getConfig().engageRadius;
    int count = 0;
    for (Entity entity : p.getNearbyEntities(radius, radius, radius)) {
      if (entity == p) {
        continue;
      }

      if (entity instanceof Monster || entity instanceof Player) {
        count++;
        if (count > 1) {
          return count;
        }
      }
    }
    return count;
  }

  private void focusFx(Player p, boolean offensive) {
    if (!fxThrottle.isReady(p.getUniqueId(), 260L)) {
      return;
    }

    fxThrottle.mark(p.getUniqueId());
    if (offensive) {
      fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
          .dustBurst(FOCUS, 3, 0.25D, 0.9F)
          .particle(Particle.CRIT, 4, 0, 0, 0, 0.2D, 0.05D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.6F);
    } else {
      fx(p.getLocation().add(0, 1, 0), FxPriority.COMBAT)
          .dustRing(FOCUS, 0.9D, 8, 0.9F)
          .sound(Sound.ITEM_SHIELD_BLOCK, 0.4F, 1.4F);
    }
  }

  private double getBonusDamage(int level) {
    return getConfig().bonusDamageBase + (getLevelPercent(level) * getConfig().bonusDamageFactor);
  }

  private double getDamageReduction(int level) {
    return Math.min(getConfig().maxReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
  }


  @ConfigDescription("Bonus damage and damage reduction while exactly one hostile is engaged with you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Base for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageBase = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Factor for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Base for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Factor for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reduction for the Swords Duelists Focus adaptation.", impact = "Caps damage reduction at the requested maximum.")
    double maxReduction = 0.40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius in blocks used to detect engaged hostiles.", impact = "Higher values count hostiles from farther away when deciding if you are in a duel.")
    double engageRadius = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Focused Hit for the Swords Duelists Focus adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerFocusedHit = 4;

    public Config() {
      baseCost = 5;
      costFactor = 0.68;
      maxLevel = 5;
      initialCost = 5;
    }
  }
}
