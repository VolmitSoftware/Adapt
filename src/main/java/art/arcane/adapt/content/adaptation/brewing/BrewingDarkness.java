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
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;


public class BrewingDarkness extends SimpleAdaptation<BrewingDarkness.Config> {
  public BrewingDarkness() {
    super("brewing-darkness");
    registerConfiguration(Config.class);
    setIcon(Material.BLACK_CONCRETE);
    setInterval(1335);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-darkness")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Material.BLACK_CONCRETE)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.NIGHT_VISION))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.DARKNESS_NAME))
            .setColor(Color.BLACK)
            .addEffect(PotionEffectType.DARKNESS, 600, 100, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BREWING_STAND)
        .key("challenge_brewing_darkness_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_brewing_darkness_25", "brewing.darkness.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.DARKNESS_LORE1));
    v.addLore(C.GRAY + "- " + AdaptLanguage.text(BrewingMessages.DARKNESS_LORE2));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!getBrewingRecipes().contains(e.getRecipe())) {
      return;
    }
    getServer().addStat(e.getBrewerId(), "brewing.darkness.potions-brewed", e.getBrewedPotions());
    Location loc = e.getBlock().getLocation().add(0.5D, 0.6D, 0.5D);
    timeline(loc)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particle.SCULK_SOUL, 1.0D - (0.8D * progress), 12, 0.4D);
          if (tick == 0) {
            f.chord(Sound.BLOCK_SCULK_CATALYST_BREAK, 0.5F, 1.0F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.5F);
          }
          if (tick == 5) {
            f.sound(Sound.BLOCK_BREWING_STAND_BREW, 0.6F, 0.7F);
          }
        })
        .start();
  }



  @ConfigDescription("Brew a Potion of Darkness from NightVision Potion and Black Concrete.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
