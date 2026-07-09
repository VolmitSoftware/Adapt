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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BlockingHorseArmorer extends SimpleAdaptation<BlockingHorseArmorer.Config> {

  public BlockingHorseArmorer() {
    super("blocking-horsearmorer");
    registerConfiguration(Config.class);
    setLocalizationKey("blocking.horse_armorer");
    setIcon(Material.GOLDEN_HORSE_ARMOR);
    setInterval(17774);
    registerRecipe(AdaptRecipe.shaped()
        .key("blocking-horsearmorerleather")
        .ingredient(new MaterialChar('I', Material.LEATHER))
        .ingredient(new MaterialChar('U', Material.SADDLE))
        .shapes(List.of(
            "III",
            "IUI",
            "III"))
        .result(new ItemStack(Material.LEATHER_HORSE_ARMOR, 1))
        .build());
    registerRecipe(AdaptRecipe.shaped()
        .key("blocking-horsearmoreriron")
        .ingredient(new MaterialChar('I', Material.IRON_INGOT))
        .ingredient(new MaterialChar('U', Material.SADDLE))
        .shapes(List.of(
            "III",
            "IUI",
            "III"))
        .result(new ItemStack(Material.IRON_HORSE_ARMOR, 1))
        .build());
    registerRecipe(AdaptRecipe.shaped()
        .key("blocking-horsearmorergold")
        .ingredient(new MaterialChar('I', Material.GOLD_INGOT))
        .ingredient(new MaterialChar('U', Material.SADDLE))
        .shapes(List.of(
            "III",
            "IUI",
            "III"))
        .result(new ItemStack(Material.GOLDEN_HORSE_ARMOR, 1))
        .build());
    registerRecipe(AdaptRecipe.shaped()
        .key("blocking-horsearmorerdiamond")
        .ingredient(new MaterialChar('I', Material.DIAMOND))
        .ingredient(new MaterialChar('U', Material.SADDLE))
        .shapes(List.of(
            "III",
            "IUI",
            "III"))
        .result(new ItemStack(Material.DIAMOND_HORSE_ARMOR, 1))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_HORSE_ARMOR)
        .key("challenge_blocking_horse_armor_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_blocking_horse_armor_10", "blocking.horse-armorer.armor-crafted", 10, 400);
  }

  @EventHandler
  public void on(CraftItemEvent e) {
    if (e.getWhoClicked() instanceof Player p && hasActiveAdaptation(p) && isAdaptationRecipe(e.getRecipe())) {
      addStat(p, "blocking.horse-armorer.armor-crafted", 1);
      fx(p, FxPriority.TRANSITION)
          .column(Particle.WAX_ON, 5, 1.4D)
          .burst(Particles.CRIT_MAGIC, 4, 0.3D)
          .chord(Sound.ENTITY_HORSE_SADDLE, 0.6F, 1.0F, Sound.BLOCK_ANVIL_USE, 0.3F, 1.3F);
    }
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("blocking.horse_armorer.lore1"));
  }


  @Override
  public void onTick() {
  }

  @ConfigDescription("Craft Horse Armor by surrounding a saddle with material.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      enabled = false;
      permanent = true;
      baseCost = 5;
      costFactor = 0;
      maxLevel = 1;
      initialCost = 1;
    }
  }
}
