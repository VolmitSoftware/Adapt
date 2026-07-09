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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class TragoulDeathSense extends SimpleAdaptation<TragoulDeathSense.Config> {
  public TragoulDeathSense() {
    super("tragoul-death-sense");
    registerConfiguration(Config.class);
    setIcon(Material.SPIDER_EYE);
    setInterval(1250);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPIDER_EYE)
        .key("challenge_tragoul_death_sense_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_tragoul_death_sense_1k", "tragoul.death-sense.prey-sensed", 1000, 600);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.death_sense.lore1"));
    v.addLore(C.GREEN + "+ " + Form.pc(getHealthThreshold(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.death_sense.lore2"));
    v.addLore(C.YELLOW + "* " + Form.f(getRadius(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.death_sense.lore3"));
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }

      withPlayerThread(p, () -> {
        int level = getActiveLevel(p);
        if (level <= 0) {
          return;
        }

        double radius = getRadius(level);
        double threshold = getHealthThreshold(level);
        int glowTicks = getConfig().glowTicks;
        int sensed = 0;
        for (Entity entity : p.getNearbyEntities(radius, radius, radius)) {
          if (!(entity instanceof Monster monster) || monster.isDead() || !monster.isValid()) {
            continue;
          }

          if (monster.hasPotionEffect(PotionEffectType.GLOWING)) {
            continue;
          }

          if (isProtectedFriendly(p, monster)) {
            continue;
          }

          IAttribute attribute = Version.get().getAttribute(monster, Attributes.GENERIC_MAX_HEALTH);
          double maxHealth = attribute == null ? 20D : attribute.getValue();
          if (maxHealth <= 0 || monster.getHealth() / maxHealth > threshold) {
            continue;
          }

          J.runEntity(monster, () -> {
            if (monster.isValid() && !monster.isDead()) {
              monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0, true, false, false), true);
              fx(monster.getLocation().add(0, 0.8, 0), FxPriority.AMBIENT)
                  .particle(Particle.SCULK_SOUL, 2, 0, 0.3, 0, 0.15, 0.01);
            }
          });
          sensed++;
          if (sensed >= getConfig().maxMarksPerPulse) {
            break;
          }
        }

        if (sensed > 0) {
          adaptPlayer.getData().addStat("tragoul.death-sense.prey-sensed", sensed);
          float pitch = (float) (0.5 + (Math.min(sensed, 4) * 0.08));
          fx(p.getLocation().add(0, 1.0, 0), FxPriority.AMBIENT)
              .particle(Particle.SCULK_SOUL, 1, 0, 0, 0, 0.1, 0.01)
              .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.2F, pitch);
        }
      });
    }
  }

  private double getRadius(int level) {
    return Math.max(2, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
  }

  private double getHealthThreshold(int level) {
    return Math.min(getConfig().maxHealthThreshold,
        Math.max(0, getConfig().healthThresholdBase + (getLevelPercent(level) * getConfig().healthThresholdFactor)));
  }

  @ConfigDescription("Weakened hostile mobs near you briefly glow so you can sense dying prey through walls.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scan radius before level scaling.", impact = "Higher values sense prey further away but cost more scan work.")
    double radiusBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional scan radius granted at max level.", impact = "Higher values increase level-scaled radius growth.")
    double radiusFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Health fraction below which hostile mobs are sensed, before level scaling.", impact = "Higher values mark healthier mobs as weakened prey.")
    double healthThresholdBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional health fraction granted at max level.", impact = "Higher values increase level-scaled threshold growth.")
    double healthThresholdFactor = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the sensed health fraction.", impact = "Prevents marking near-full-health mobs at high levels.")
    double maxHealthThreshold = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the glow applied to sensed mobs.", impact = "Higher values keep prey highlighted longer between pulses.")
    int glowTicks = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum mobs marked per scan pulse.", impact = "Caps per-pulse work to protect server performance.")
    int maxMarksPerPulse = 8;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
