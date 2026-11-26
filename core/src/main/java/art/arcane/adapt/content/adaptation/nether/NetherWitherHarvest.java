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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;


public class NetherWitherHarvest extends SimpleAdaptation<NetherWitherHarvest.Config> {
  public NetherWitherHarvest() {
    super("nether-wither-harvest");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone"));
    setInterval(5000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_nether_harvest_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_skeleton_skull"))
            .key("challenge_nether_harvest_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_harvest_100", "nether.wither-harvest.skeletons-harvested", 100, 300);
    registerMilestone("challenge_nether_harvest_2500", "nether.wither-harvest.skeletons-harvested", 2500, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getBonusBones(level), 1);
    statLore(v, getBonusCoal(level), 2);
    statLore(v, Form.pc(getSkullChance(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDeathEvent e) {
    PlayerRef p = NetherSkeletonHarvest.harvester(e);
    if (p == null) {
      return;
    }

    withAdaptedPlayer(p, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      boolean skull = NetherSkeletonHarvest.harvest(this, e, getBonusBones(level), getBonusCoal(level), getSkullChance(level));
      addStat(p, "nether.wither-harvest.skeletons-harvested", 1);
      xp(p, getConfig().xpPerHarvest);
      if (skull) {
        xp(p, getConfig().xpOnSkull);
      }
    });
  }

  private int getBonusBones(int level) {
    return bonusAmount(getLevelPercent(level), getConfig().bonusBonesBase, getConfig().bonusBonesFactor);
  }

  private int getBonusCoal(int level) {
    return bonusAmount(getLevelPercent(level), getConfig().bonusCoalBase, getConfig().bonusCoalFactor);
  }

  private double getSkullChance(int level) {
    return skullChance(getLevelPercent(level), getConfig().skullChanceBase, getConfig().skullChanceFactor, getConfig().maxSkullChance);
  }

  public static int bonusAmount(double levelPercent, double base, double factor) {
    return Math.max(1, (int) Math.round(base + (levelPercent * factor)));
  }

  public static double skullChance(double levelPercent, double base, double factor, double cap) {
    return Math.min(cap, base + (levelPercent * factor));
  }


  static void harvestFx(Adaptation<?> owner, LivingRef skeleton, boolean skull) {
    if (skull) {
      Fx.now(owner, skeleton.location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
          .column(Particles.END_ROD, 4, 1.4D)
          .particle(Particles.SOUL, 4, 0D, 0.3D, 0D, 0.2D, 0.0D)
          .chord(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.2F, Sounds.ENTITY_WITHER_SKELETON_DEATH, 0.4F, 1.3F);
    } else {
      Fx.now(owner, skeleton.location().add(0D, 0.6D, 0D), FxPriority.GAMEPLAY)
          .particle(Particles.SOUL, 3, 0.2D, 0.2D, 0.2D, 0.15D, 0.0D)
          .particle(Particles.SMOKE, 2, 0D, 0.1D, 0D, 0.15D, 0.0D)
          .sound(Sounds.ENTITY_WITHER_SKELETON_HURT, 0.4F, 1.1F);
    }
  }

  @ConfigDescription("Wither skeletons yield extra bones and coal, with slightly improved skull odds.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Bones Base for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusBonesBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Bones Factor for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusBonesFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Coal Base for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusCoalBase = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Coal Factor for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusCoalFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skull Chance Base for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skullChanceBase = 0.03;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skull Chance Factor for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skullChanceFactor = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Skull Chance for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxSkullChance = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Harvest for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerHarvest = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Skull for the Nether Wither Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnSkull = 40;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }
}
