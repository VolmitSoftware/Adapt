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
import art.arcane.adapt.util.reflect.registries.PotionTypes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.potion.PotionEffectType;


public class BrewingSaturation extends SimpleAdaptation<BrewingSaturation.Config> {
  public BrewingSaturation() {
    super("brewing-saturation");
    registerConfiguration(Config.class);
    setIcon(Material.BAKED_POTATO);
    setInterval(1334);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-saturation-1")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Material.BAKED_POTATO)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionTypes.REGEN))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.SATURATION_NAME))
            .setColor(Color.ORANGE)
            .addEffect(PotionEffectType.SATURATION, 1, 4, true, true, true)
            .build())
        .build());
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-saturation-2")
        .brewingTime(320)
        .fuelCost(32)
        .ingredient(Material.HAY_BLOCK)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionTypes.REGEN))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.SATURATION_STRONG_NAME))
            .setColor(Color.ORANGE)
            .addEffect(PotionEffectType.SATURATION, 1, 8, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_CARROT)
        .key("challenge_brewing_saturation_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_saturation_25", "brewing.saturation.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.SATURATION_LORE1));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.SATURATION_LORE2));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!getBrewingRecipes().contains(e.getRecipe())) {
      return;
    }
    getServer().addStat(e.getBrewerId(), "brewing.saturation.potions-brewed", e.getBrewedPotions());
    Location loc = e.getBlock().getLocation().add(0.5D, 0.6D, 0.5D);
    fx(loc, FxPriority.TRANSITION)
        .dustBurst(Color.ORANGE, 8, 0.3D, 1.2F)
        .particle(Particle.CAMPFIRE_COSY_SMOKE, 3, 0, 0.3D, 0, 0.2D, 0.02D)
        .chord(Sound.ENTITY_GENERIC_EAT, 0.3F, 1.2F, Sound.BLOCK_BREWING_STAND_BREW, 0.6F, 1.1F);
  }



  @ConfigDescription("Brew a Potion of Saturation from Regen Potion and Baked Potato.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
