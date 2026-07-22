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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HerbalismMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Materials;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class HerbalismLuck extends SimpleAdaptation<HerbalismLuck.Config> {

  public HerbalismLuck() {
    super("herbalism-luck");
    registerConfiguration(Config.class);
    setIcon(Material.EMERALD);
    setInterval(8121);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RABBIT_FOOT)
        .key("challenge_herbalism_luck_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.EMERALD)
            .key("challenge_herbalism_luck_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_herbalism_luck_100", "herbalism.luck.lucky-drops", 100, 300);
    registerMilestone("challenge_herbalism_luck_2500", "herbalism.luck.lucky-drops", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(HerbalismMessages.LUCK_LORE0));
    statLore(v, "(" + Form.f(getEffectiveness(level), 1) + "%) +", 1);
    statLore(v, "(" + Form.f(getEffectiveness(level), 1) + "%) +", 2);
  }

  private double getEffectiveness(double factor) {
    return Math.min(getConfig().highChance, factor * factor + getConfig().lowChance);
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void on(BlockDropItemEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    Block broken = e.getBlock();
    Material brokenType = e.getBlockState().getType();
    if (brokenType == Materials.GRASS || brokenType == Material.TALL_GRASS) {
      double d = ThreadLocalRandom.current().nextDouble(100D);
      Material m = ItemListings.getHerbalLuckSeeds().getRandom();
      if (d < getEffectiveness(getLevel(p))) {
        xp(p, 100);
        addStat(p, "herbalism.luck.lucky-drops", 1);
        ItemStack luckDrop = new ItemStack(m, 1);
        e.getBlock().getWorld().dropItem(e.getBlock().getLocation(), luckDrop);
        luckySparkle(broken.getLocation());
      }
    }

    if (ItemListings.getFlowers().contains(brokenType)) {
      double d = ThreadLocalRandom.current().nextDouble(100D);
      Material m = ItemListings.getHerbalLuckFood().getRandom();
      if (d < getEffectiveness(getLevel(p))) {
        xp(p, 100);
        addStat(p, "herbalism.luck.lucky-drops", 1);
        ItemStack luckDrop = new ItemStack(m, 1);
        e.getBlock().getWorld().dropItem(e.getBlock().getLocation(), luckDrop);
        luckySparkle(broken.getLocation());
      }
    }

  }

  private void luckySparkle(Location loc) {
    fx(loc.clone().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .dustRing(Color.fromRGB(120, 230, 120), 0.5D, 12, 1.0F)
        .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.3D, 0, 0.35D, 0.05D)
        .particle(Particle.WAX_ON, 2, 0, 0.3D, 0, 0.3D, 0.02D)
        .chord(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.4F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.8F);
  }



  @ConfigDescription("Breaking Grass or Flowers has a chance to drop random items.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Low Chance for the Herbalism Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lowChance = 0.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls High Chance for the Herbalism Luck adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double highChance = 90;

    public Config() {
      baseCost = 8;
      costFactor = 0.75;
      maxLevel = 7;
      initialCost = 3;
    }
  }
}
