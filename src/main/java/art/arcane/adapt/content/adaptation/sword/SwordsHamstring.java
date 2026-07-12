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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class SwordsHamstring extends SimpleAdaptation<SwordsHamstring.Config> {
  private static final Color HAMSTRING = Color.fromRGB(0x8FE3FF);

  public SwordsHamstring() {
    super("sword-hamstring");
    registerConfiguration(Config.class);
    setIcon(Material.LEAD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_hamstring_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_swords_hamstring_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_swords_hamstring_200", "swords.hamstring.hamstrings", 200, 400);
    registerMilestone("challenge_swords_hamstring_2500", "swords.hamstring.hamstrings", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getSlowTier(level) + 1D, 0), 1);
    statLore(v, Form.duration(getDurationTicks(level) * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null) {
      return;
    }

    LivingEntity target = combat.target();
    if (!isFleeing(target)) {
      return;
    }

    int amplifier = getSlowTier(combat.level());
    int duration = getDurationTicks(combat.level());
    J.runEntity(target, () -> {
      if (!target.isValid()) {
        return;
      }

      target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier, true, true, true), true);
      if (target instanceof Player playerTarget) {
        playerTarget.setSprinting(false);
      }
    });

    addStat(combat.attacker(), "swords.hamstring.hamstrings", 1);
    xp(combat.attacker(), getConfig().xpPerHamstring);
    fx(target.getLocation().add(0, 0.3D, 0), FxPriority.COMBAT)
        .dustBurst(HAMSTRING, 6, 0.3D, 1.0F)
        .particle(Particle.CRIT, 4, 0.2D, 0, 0.2D, 0.05D, 0.02D)
        .chord(Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.5F, 0.8F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35F, 0.7F);
  }

  private boolean isFleeing(LivingEntity target) {
    if (target instanceof Player playerTarget && playerTarget.isSprinting()) {
      return true;
    }

    Vector velocity = target.getVelocity();
    double horizontal = Math.sqrt((velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ()));
    return horizontal >= getConfig().fleeSpeedThreshold;
  }

  private int getSlowTier(int level) {
    return Math.max(0, (int) Math.round(getConfig().slowTierBase + (getLevelPercent(level) * getConfig().slowTierFactor)));
  }

  private int getDurationTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
  }


  @ConfigDescription("Hitting a sprinting or fleeing target slows it and briefly stops it sprinting.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Tier Base for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Tier Factor for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksFactor = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal speed at which a target counts as fleeing.", impact = "Lower values slow targets more readily; higher values require faster movement.")
    double fleeSpeedThreshold = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Hamstring for the Swords Hamstring adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerHamstring = 5;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
