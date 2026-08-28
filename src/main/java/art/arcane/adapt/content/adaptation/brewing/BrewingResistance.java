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

package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BrewingMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.potion.AdaptBrewCompleteEvent;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;


public class BrewingResistance extends SimpleAdaptation<BrewingResistance.Config> {
  public BrewingResistance() {
    super("brewing-resistance");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_BLOCK);
    setInterval(1333);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-resistance-1")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Material.IRON_INGOT)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.AWKWARD))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.RESISTANCE_NAME))
            .setColor(Color.WHITE)
            .addEffect(PotionEffectType.RESISTANCE, 1200, 0, true, true, true)
            .build())
        .build());
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-resistance-2")
        .brewingTime(320)
        .fuelCost(32)
        .ingredient(Material.IRON_BLOCK)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.AWKWARD))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.RESISTANCE_STRONG_NAME))
            .setColor(Color.WHITE)
            .addEffect(PotionEffectType.RESISTANCE, 600, 1, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE)
        .key("challenge_brewing_resistance_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_resistance_25", "brewing.resistance.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.RESISTANCE_LORE1));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.RESISTANCE_LORE2));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!getBrewingRecipes().contains(e.getRecipe())) {
      return;
    }
    getServer().addStat(e.getBrewerId(), "brewing.resistance.potions-brewed", e.getBrewedPotions());
    Location loc = e.getBlock().getLocation().add(0.5D, 0.6D, 0.5D);
    timeline(loc)
        .duration(4)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24.0D)
        .frame((f, tick, progress) -> {
          f.dustRing(Color.WHITE, tick < 3 ? 0.9D : 0.35D, 10, 1.2F);
          if (tick == 0) {
            f.particle(Particles.END_ROD, 3, 0, 0.3D, 0, 0.2D, 0.02D)
                .chord(Sound.BLOCK_ANVIL_LAND, 0.2F, 1.9F, Sound.BLOCK_BREWING_STAND_BREW, 0.6F, 1.0F);
          }
        })
        .start();
  }



  @ConfigDescription("Brew a Potion of Resistance from Awkward Potion and Iron.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
