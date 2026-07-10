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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CraftingBackpacks extends SimpleAdaptation<CraftingBackpacks.Config> {

  public CraftingBackpacks() {
    super("crafting-backpacks");
    registerConfiguration(Config.class);
    setIcon(Material.BUNDLE);
    setInterval(17779);
    registerRecipe(AdaptRecipe.shaped()
        .key("crafting-backpacks")
        .ingredient(new MaterialChar('I', Material.LEATHER))
        .ingredient(new MaterialChar('L', Material.LEAD))
        .ingredient(new MaterialChar('C', Material.CHEST))
        .ingredient(new MaterialChar('X', Material.BARREL))
        .shapes(List.of(
            "ILI",
            "IXI",
            "ICI"))
        .result(new ItemStack(Material.BUNDLE, 1))
        .build());
    AdvancementSpec backpacksCrafted = AdvancementSpec.challenge(
        "challenge_crafting_backpack_25",
        Material.BUNDLE,
        Localizer.dLocalize("advancement.challenge_crafting_backpack_25.title"),
        Localizer.dLocalize("advancement.challenge_crafting_backpack_25.description")
    );
    registerMilestone(backpacksCrafted, "crafting.backpacks.bundles-crafted", 25, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting.backpacks.lore1"));
    v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.backpacks.lore2"));
    v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.backpacks.lore3"));
    v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.backpacks.lore4"));

  }


  @EventHandler
  public void on(CraftItemEvent e) {
    Player p = (Player) e.getWhoClicked();
    if (!hasActiveAdaptation(p)) return;
    if (e.getRecipe() != null && e.getRecipe().getResult().getType() == Material.BUNDLE) {
      addStat(p, "crafting.backpacks.bundles-crafted", 1);
      cinchDrawstring(p.getLocation().add(0, 1, 0));
    }
  }

  private void cinchDrawstring(Location center) {
    timeline(center)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          double radius = 0.8D - (0.7D * progress);
          fx.ring(Particles.CRIT_MAGIC, radius, 12, 0.0D);
          fx.particle(Particle.PORTAL, 2, 0, 0.2D, 0, radius, 0.02D);
          if (tick == 0) {
            fx.dustRing(Color.fromRGB(160, 120, 60), 0.6D, 16, 1.0F);
            fx.chord(Sound.ITEM_BUNDLE_INSERT, 0.8F, 0.8F, Sound.BLOCK_WOOL_PLACE, 0.6F, 1.2F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 1.0F);
          }
        })
        .start();
  }


  @ConfigDescription("Craft Bundles for portable item storage.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
