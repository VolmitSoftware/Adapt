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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.potion.PotionType;


public class BrewingFatigue extends SimpleAdaptation<BrewingFatigue.Config> {
  public BrewingFatigue() {
    super("brewing-fatigue");
    registerConfiguration(Config.class);
    setIcon(Material.SLIME_BALL);
    setInterval(1332);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-fatigue-1")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Material.SLIME_BALL)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.WEAKNESS))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName("Bottled Fatigue")
            .setColor(Color.fromRGB(0, 66, 0))
            .addEffect(PotionEffectTypes.SLOW_DIGGING, 1200, 1, true, true, true)
            .build())
        .build());
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-fatigue-2")
        .brewingTime(320)
        .fuelCost(32)
        .ingredient(Material.SLIME_BLOCK)
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, PotionType.WEAKNESS))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName("Bottled Fatigue 2")
            .setColor(Color.fromRGB(0, 66, 0))
            .addEffect(PotionEffectTypes.SLOW_DIGGING, 600, 2, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BREWING_STAND)
        .key("challenge_brewing_fatigue_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_brewing_fatigue_25", "brewing.fatigue.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing.fatigue.lore1"));
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("brewing.fatigue.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BrewEvent e) {
    BrewingStandOwner owner = WorldData.of(e.getBlock().getWorld()).get(e.getBlock(), BrewingStandOwner.class);
    if (owner != null) {
      getServer().peekData(owner.getOwner()).addStat("brewing.fatigue.potions-brewed", 1);
      Location loc = e.getBlock().getLocation().add(0.5D, 0.6D, 0.5D);
      fx(loc, FxPriority.TRANSITION)
          .dustBurst(Color.fromRGB(0x00, 0x42, 0x00), 10, 0.25D, 1.3F)
          .particle(Particles.SMOKE, 3, 0, 0.1D, 0, 0.2D, 0.01D)
          .chord(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.4F, 0.5F, Sound.BLOCK_BREWING_STAND_BREW, 0.6F, 0.6F);
    }
  }

  @Override
  public void onTick() {
  }


  @ConfigDescription("Brew a Potion of Fatigue from Weakness Potion and Slime.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
