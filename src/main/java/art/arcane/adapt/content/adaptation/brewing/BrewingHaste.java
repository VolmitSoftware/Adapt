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
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.adapt.util.reflect.registries.PotionTypes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;


public class BrewingHaste extends SimpleAdaptation<BrewingHaste.Config> {
  public BrewingHaste() {
    super("brewing-haste");
    registerConfiguration(Config.class);
    setIcon(Material.AMETHYST_SHARD);
    setInterval(1334);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-haste-1")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Material.AMETHYST_SHARD)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionTypes.SPEED))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.HASTE_NAME))
            .setColor(Color.YELLOW)
            .addEffect(PotionEffectTypes.FAST_DIGGING, 1200, 0, true, true, true)
            .build())
        .build());
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-haste-2")
        .brewingTime(320)
        .fuelCost(32)
        .ingredient(Material.AMETHYST_BLOCK)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionTypes.SPEED))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.HASTE_STRONG_NAME))
            .setColor(Color.YELLOW)
            .addEffect(PotionEffectTypes.FAST_DIGGING, 600, 1, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BREWING_STAND)
        .key("challenge_brewing_haste_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_haste_25", "brewing.haste.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.HASTE_LORE1));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.HASTE_LORE2));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!getBrewingRecipes().contains(e.getRecipe())) {
      return;
    }
    getServer().addStat(e.getBrewerId(), "brewing.haste.potions-brewed", e.getBrewedPotions());
    Location loc = e.getBlock().getLocation().add(0.5D, 0.6D, 0.5D);
    Color amethyst = Color.fromRGB(0x9B, 0x59, 0xB6);
    timeline(loc)
        .duration(3)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24.0D)
        .frame((f, tick, progress) -> {
          f.dustRing(amethyst, 0.3D + (0.7D * progress), 12, 0.6F);
          f.particle(Particles.CRIT_MAGIC, 2, 0, 0.3D, 0, 0.25D, 0.05D);
          if (tick == 0) {
            f.particle(Particles.END_ROD, 3, 0, 0.3D, 0, 0.2D, 0.03D)
                .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F, Sound.BLOCK_BREWING_STAND_BREW, 0.6F, 1.2F, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.4F, 1.8F);
          }
        })
        .start();
  }



  @ConfigDescription("Brew a Potion of Haste from Speed Potion and Amethyst.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
