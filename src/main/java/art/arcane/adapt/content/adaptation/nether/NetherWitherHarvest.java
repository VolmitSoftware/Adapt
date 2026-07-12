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
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NetherWitherHarvest extends SimpleAdaptation<NetherWitherHarvest.Config> {
  public NetherWitherHarvest() {
    super("nether-wither-harvest");
    registerConfiguration(Config.class);
    setIcon(Material.BONE);
    setInterval(5000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_nether_harvest_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WITHER_SKELETON_SKULL)
            .key("challenge_nether_harvest_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_nether_harvest_100", "nether.wither-harvest.skeletons-harvested", 100, 300);
    registerMilestone("challenge_nether_harvest_2500", "nether.wither-harvest.skeletons-harvested", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getBonusBones(level), 1);
    statLore(v, getBonusCoal(level), 2);
    statLore(v, Form.pc(getSkullChance(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity() instanceof WitherSkeleton skeleton)) {
      return;
    }

    Player p = skeleton.getKiller();
    if (p == null) {
      return;
    }

    withAdaptedPlayer(p, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      List<ItemStack> drops = e.getDrops();
      drops.add(new ItemStack(Material.BONE, getBonusBones(level)));
      drops.add(new ItemStack(Material.COAL, getBonusCoal(level)));

      boolean skull = false;
      if (ThreadLocalRandom.current().nextDouble() <= getSkullChance(level) && !containsSkull(drops)) {
        drops.add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
        skull = true;
      }

      addStat(p, "nether.wither-harvest.skeletons-harvested", 1);
      xp(p, getConfig().xpPerHarvest);
      if (skull) {
        xp(p, getConfig().xpOnSkull);
        fx(e.getEntity().getLocation().add(0D, 1.0D, 0D), FxPriority.COMBAT)
            .column(Particles.END_ROD, 4, 1.4D)
            .particle(Particle.SOUL, 4, 0D, 0.3D, 0D, 0.2D, 0.0D)
            .chord(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.2F, Sound.ENTITY_WITHER_SKELETON_DEATH, 0.4F, 1.3F);
      } else {
        fx(e.getEntity().getLocation().add(0D, 0.6D, 0D), FxPriority.GAMEPLAY)
            .particle(Particle.SOUL, 3, 0.2D, 0.2D, 0.2D, 0.15D, 0.0D)
            .particle(Particles.SMOKE, 2, 0D, 0.1D, 0D, 0.15D, 0.0D)
            .sound(Sound.ENTITY_WITHER_SKELETON_HURT, 0.4F, 1.1F);
      }
    });
  }

  private boolean containsSkull(List<ItemStack> drops) {
    for (ItemStack drop : drops) {
      if (drop != null && drop.getType() == Material.WITHER_SKELETON_SKULL) {
        return true;
      }
    }

    return false;
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

  static int bonusAmount(double levelPercent, double base, double factor) {
    return Math.max(1, (int) Math.round(base + (levelPercent * factor)));
  }

  static double skullChance(double levelPercent, double base, double factor, double cap) {
    return Math.min(cap, base + (levelPercent * factor));
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
      baseCost = 3;
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
