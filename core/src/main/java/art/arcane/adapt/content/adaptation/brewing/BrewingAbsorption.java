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
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BrewingMessages;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

public class BrewingAbsorption extends SimpleAdaptation<BrewingAbsorption.Config> {
  public BrewingAbsorption() {
    super("brewing-absorption");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("quartz"));
    setInterval(1333);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-absorption-1")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Id.minecraft("quartz"))
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, Id.minecraft("healing")))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.ABSORPTION_NAME))
            .setColor(Rgb.of(0x808080))
            .addEffect(Id.minecraft("absorption"), 1200, 0, true, true, true)
            .build())
        .build()
    );
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-absorption-2")
        .brewingTime(320)
        .fuelCost(32)
        .ingredient(Id.minecraft("quartz_block"))
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, Id.minecraft("healing")))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.ABSORPTION_STRONG_NAME))
            .setColor(Rgb.of(0x808080))
            .addEffect(Id.minecraft("absorption"), 600, 1, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_apple"))
        .key("challenge_brewing_absorption_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_absorption_25", "brewing.absorption.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.ABSORPTION_LORE1));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.ABSORPTION_LORE2));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!isBrewingRecipe(e.recipeId())) {
      return;
    }
    getServer().addStat(e.brewerId(), "brewing.absorption.potions-brewed", e.brewedPotions());
    Loc loc = e.block().location().add(0.5D, 0.6D, 0.5D);
    Rgb gold = new Rgb(0xF7, 0xD5, 0x1D);
    timeline(loc)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24.0D)
        .frame((f, tick, progress) -> {
          f.dustRing(gold, 0.9D - (0.5D * progress), 12, 0.9F);
          if (tick == 0) {
            f.particle(Particles.END_ROD, 4, 0, 0.3D, 0, 0.15D, 0.02D)
                .chord(Sounds.BLOCK_BREWING_STAND_BREW, 0.7F, 1.4F, Sounds.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.6F);
          }
        })
        .start();
  }

  @ConfigDescription("Brew a Potion of Absorption from Instant Heal and Quartz.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 2;
      permanent = true;
      baseCost = 2;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
