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

public class BrewingDecay extends SimpleAdaptation<BrewingDecay.Config> {
  public BrewingDecay() {
    super("brewing-decay");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wither_rose"));
    setInterval(1334);
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-decay-1")
        .brewingTime(320)
        .fuelCost(16)
        .ingredient(Id.minecraft("poisonous_potato"))
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, Id.minecraft("weakness")))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.DECAY_NAME))
            .setColor(Rgb.of(0x800000))
            .addEffect(Id.minecraft("wither"), 320, 0, true, true, true)
            .build())
        .build());
    registerBrewingRecipe(BrewingRecipe.builder()
        .id("brewing-decay-2")
        .brewingTime(320)
        .fuelCost(32)
        .ingredient(Id.minecraft("crimson_roots"))
        .basePotion(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, Id.minecraft("weakness")))
        .result(PotionBuilder.of(PotionBuilder.Type.REGULAR)
            .setName(AdaptLanguage.text(BrewingMessages.DECAY_STRONG_NAME))
            .setColor(Rgb.of(0x800000))
            .addEffect(Id.minecraft("wither"), 160, 1, true, true, true)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_rose"))
        .key("challenge_brewing_decay_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_decay_25", "brewing.decay.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.DECAY_LORE1));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(BrewingMessages.DECAY_LORE2));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!isBrewingRecipe(e.recipeId())) {
      return;
    }
    getServer().addStat(e.brewerId(), "brewing.decay.potions-brewed", e.brewedPotions());
    Loc loc = e.block().location().add(0.5D, 0.6D, 0.5D);
    fx(loc, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 8, 0, 0.3D, 0, 0.2D, 0.03D)
        .dustBurst(new Rgb(0x2E, 0x14, 0x14), 6, 0.3D, 1.2F)
        .particle(Particles.ASH, 3, 0, 0.4D, 0, 0.3D, 0.01D)
        .chord(Sounds.ENTITY_WITHER_SHOOT, 0.3F, 1.4F, Sounds.BLOCK_BREWING_STAND_BREW, 0.6F, 0.5F);
  }

  @ConfigDescription("Brew a Potion of Wither from Weakness Potion and Poisonous Potato.")
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
