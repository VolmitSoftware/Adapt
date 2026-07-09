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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class HerbalismGrowthAura extends SimpleAdaptation<HerbalismGrowthAura.Config> {
  public HerbalismGrowthAura() {
    super("herbalism-growth-aura");
    registerConfiguration(Config.class);
    setIcon(Material.BONE_MEAL);
    setInterval(850);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WHEAT)
        .key("challenge_herbalism_growth_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.HAY_BLOCK)
            .key("challenge_herbalism_growth_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_herbalism_growth_1k", "herbalism.growth-aura.blocks-grown", 1000, 300);
    registerMilestone("challenge_herbalism_growth_25k", "herbalism.growth-aura.blocks-grown", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(getLevelPercent(level)), 0), 1);
    statLore(v, Form.pc(getStrength(level), 0), 2);
    statLore(v, C.YELLOW, "+ ", Form.f(getFoodCost(getLevelPercent(level)), 2), 3);
  }

  private double getRadius(double factor) {
    return factor * getConfig().radiusFactor;
  }

  private double getStrength(int level) {
    return level * getConfig().strengthFactor;
  }

  private double getFoodCost(double factor) {
    return M.lerp(1D - factor, getConfig().maxFoodCost, getConfig().minFoodCost);
  }


  @Override
  public void onTick() {
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      try {
        if (hasActiveAdaptation(p)) {
          double rad = getRadius(getLevelPercent(p));
          double strength = getStrength(getLevel(p));
          ThreadLocalRandom random = ThreadLocalRandom.current();
          double angle = Math.toRadians(random.nextDouble(360D));
          double foodCost = getFoodCost(getLevelPercent(p));


          for (int i = 0; i < Math.min(Math.min(rad * rad, 256), 3); i++) {
            Location m = p.getLocation().clone().add(new Vector(Math.sin(angle), RNG.r.i(-1, 1), Math.cos(angle)).multiply(random.nextDouble(rad)));
            Block a = m.getBlock();
            if (getConfig().surfaceOnly) {
              int max = a.getWorld().getHighestBlockYAt(m);

              if (max + 1 != a.getY())
                continue;
            }

            if (a.getBlockData() instanceof Ageable) {
              Ageable ab = (Ageable) a.getBlockData();
              int toGrowLeft = ab.getMaximumAge() - ab.getAge();

              if (toGrowLeft > 0) {
                int add = (int) Math.max(1, Math.min(strength, toGrowLeft));
                AdaptPlayer player = getPlayer(p);
                if (ab.getMaximumAge() > ab.getAge() && player.canConsumeFood(foodCost, 10)) {
                  while (add-- > 0) {
                    J.runEntity(p, () -> {
                      if (!p.isOnline()
                          || !player.consumeFood(foodCost, 10)
                          || !(a.getBlockData() instanceof Ageable aab)
                          || aab.getAge() == aab.getMaximumAge())
                        return;

                      aab.setAge(aab.getAge() + 1);
                      a.setBlockData(aab, true);
                      addStat(p, "herbalism.growth-aura.blocks-grown", 1);
                      if (aab.getAge() >= aab.getMaximumAge()) {
                        fx(a.getLocation().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
                            .dustRing(Color.LIME, 0.6D, 10, 0.9F)
                            .particle(Particle.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.02D)
                            .chord(Sound.ITEM_CROP_PLANT, 0.35F, 1.2F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.3F, 1.6F);
                      } else {
                        fx(a.getLocation().add(0.5, 0.6, 0.5), FxPriority.AMBIENT)
                            .particle(Particle.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.02D)
                            .particle(Particle.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.02D)
                            .sound(Sound.ITEM_CROP_PLANT, 0.25F, 1.5F);
                      }
                    }, RNG.r.i(30, 60));
                  }
                }
              }


            }
          }
        }
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }
  }

  @ConfigDescription("Grow nature around you in an aura at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Surface Only for the Herbalism Growth Aura adaptation.", impact = "True enables this behavior and false disables it.")
    boolean surfaceOnly = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Food Cost for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minFoodCost = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Food Cost for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxFoodCost = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strength Factor for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strengthFactor = 0.75;

    public Config() {
      baseCost = 8;
      costFactor = 0.325;
      maxLevel = 7;
      initialCost = 12;
    }
  }
}
