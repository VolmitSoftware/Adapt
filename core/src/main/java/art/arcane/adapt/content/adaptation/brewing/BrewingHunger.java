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

public class BrewingHunger extends SimpleAdaptation<BrewingHunger.Config> {
  public BrewingHunger() {
    super("brewing-hunger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("rotten_flesh"));
    setInterval(1331);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-hunger-1")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Id.minecraft("rotten_flesh"))
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, Id.minecraft("awkward")))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.HUNGER_NAME))
            .setColor(Rgb.of(0x008000))
            .addEffect(Id.minecraft("hunger"), 1200, 0, true, true, true)
            .build())
        .build());
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-hunger-2")
        .brewingTime(320)
        .fuelCost(32)
        .ingredient(Id.minecraft("rotten_flesh"))
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, Id.minecraft("weakness")))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.HUNGER_STRONG_NAME))
            .setColor(Rgb.of(0x008000))
            .addEffect(Id.minecraft("hunger"), 600, 1, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("rotten_flesh"))
        .key("challenge_brewing_hunger_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_hunger_25", "brewing.hunger.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.HUNGER_LORE1));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.HUNGER_LORE2));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!isBrewingRecipe(e.recipeId())) {
      return;
    }
    getServer().addStat(e.brewerId(), "brewing.hunger.potions-brewed", e.brewedPotions());
    Loc loc = e.block().location().add(0.5D, 0.6D, 0.5D);
    fx(loc, FxPriority.TRANSITION)
        .particle(Particles.SNEEZE, 8, 0, 0.2D, 0, 0.25D, 0.01D)
        .dustBurst(Rgb.of(0x008000), 6, 0.3D, 1.2F)
        .chord(Sounds.ENTITY_PLAYER_BURP, 0.35F, 0.7F, Sounds.BLOCK_BREWING_STAND_BREW, 0.6F, 0.5F);
  }

  @ConfigDescription("Brew a Potion of Hunger from Awkward Potion and Rotten Flesh.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 1;
      permanent = true;
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
