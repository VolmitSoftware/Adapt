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
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class HerbalismTerralid extends SimpleAdaptation<HerbalismTerralid.Config> {

  public HerbalismTerralid() {
    super("herbalism-terralid");
    registerConfiguration(Config.class);
    setIcon(Material.GRASS_BLOCK);
    setInterval(17771);
    registerRecipe(AdaptRecipe.shaped()
        .key("herbalism-dirt-terralid")
        .ingredient(new MaterialChar('S', Material.WHEAT_SEEDS))
        .ingredient(new MaterialChar('D', Material.DIRT))
        .shapes(List.of(
            "SSS",
            "DDD"))
        .result(new ItemStack(Material.GRASS_BLOCK, 3))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GRASS_BLOCK)
        .key("challenge_herbalism_terralid_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_herbalism_terralid_200", "herbalism.terralid.grass-crafted", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("herbalism.terralid.lore1"));
  }


  @EventHandler(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || !hasActiveAdaptation(p)) {
      return;
    }
    if (e.getRecipe() instanceof org.bukkit.inventory.ShapedRecipe recipe && recipe.getKey().getNamespace().equals("adapt") && recipe.getKey().getKey().equals("herbalism-dirt-terralid")) {
      addStat(p, "herbalism.terralid.grass-crafted", 1);
      if (getPlayer(p).getData().getStat("herbalism.terralid.grass-crafted") == 1) {
        fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
            .column(Particles.END_ROD, 6, 1.4D)
            .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.6D, 0, 0.4D, 0.04D)
            .chord(Sound.BLOCK_GRASS_BREAK, 0.6F, 1.0F, Sound.ENTITY_PLAYER_LEVELUP, 0.4F, 1.5F);
      }
    }
  }


  @ConfigDescription("Craft Grass Blocks from Seeds and Dirt.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      costFactor = 0.75;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
