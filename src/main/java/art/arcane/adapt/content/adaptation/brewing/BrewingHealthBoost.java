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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.potion.AdaptBrewCompleteEvent;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
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


public class BrewingHealthBoost extends SimpleAdaptation<BrewingHealthBoost.Config> {
  public BrewingHealthBoost() {
    super("brewing-healthboost");
    registerConfiguration(Config.class);
    setLocalizationKey("brewing.health_boost");
    setIcon(Material.ENCHANTED_GOLDEN_APPLE);
    setInterval(1330);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-healthboost")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Material.GOLDEN_APPLE)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionTypes.INSTANT_HEAL))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName("Bottled Life")
            .setColor(Color.RED)
            .addEffect(PotionEffectType.HEALTH_BOOST, 1200, 1, true, true, true)
            .build())
        .build());
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-healthboost")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Material.ENCHANTED_GOLDEN_APPLE)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionTypes.INSTANT_HEAL))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName("Bottled Life")
            .setColor(Color.RED)
            .addEffect(PotionEffectType.HEALTH_BOOST, 1200, 2, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLISTERING_MELON_SLICE)
        .key("challenge_brewing_health_boost_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_brewing_health_boost_25", "brewing.health-boost.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing.health_boost.lore1"));
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing.health_boost.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!getBrewingRecipes().contains(e.getRecipe())) {
      return;
    }
    getServer().addStat(e.getBrewerId(), "brewing.health-boost.potions-brewed", e.getBrewedPotions());
    Location loc = e.getBlock().getLocation().add(0.5D, 0.6D, 0.5D);
    timeline(loc)
        .duration(4)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24.0D)
        .frame((f, tick, progress) -> {
          f.dustRing(Color.RED, 0.4D + (0.4D * progress), 8, 1.0F);
          if (tick == 0) {
            f.particle(Particle.HEART, 4, 0, 0.4D, 0, 0.25D, 0.02D)
                .chord(Sound.ENTITY_PLAYER_LEVELUP, 0.25F, 1.8F, Sound.BLOCK_BREWING_STAND_BREW, 0.6F, 1.1F);
          }
        })
        .start();
  }



  @ConfigDescription("Brew a Potion of Health Boost from Instant Heal and Golden Apple.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
