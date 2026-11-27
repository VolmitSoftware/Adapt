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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.item.Potions;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

public class BrewingSaturation extends SimpleAdaptation<BrewingSaturation.Config> {
  public static final Id ID = Id.adapt("brewing/saturation");
  public BrewingSaturation() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("baked_potato"));
    setInterval(1334);
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-saturation-1"), Id.minecraft("baked_potato"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("regeneration")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.saturation.name").resolve())
            .setColor(Rgb.of(0xFFA500))
            .addEffect(Id.minecraft("saturation"), 1, 4, true, true, true)
            .build(), 320, 16));
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-saturation-2"), Id.minecraft("hay_block"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("regeneration")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.saturation.strong_name").resolve())
            .setColor(Rgb.of(0xFFA500))
            .addEffect(Id.minecraft("saturation"), 1, 8, true, true, true)
            .build(), 320, 32));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_carrot"))
        .key("challenge_brewing_saturation_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_saturation_25", "brewing.saturation.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.saturation.lore1").resolve());
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.saturation.lore2").resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!isBrewingRecipe(Id.parseAdapt(e.recipeId()))) {
      return;
    }
    AdaptApi.players().find(e.brewerId()).ifPresent((PlayerProfile profile) -> profile.addStat("brewing.saturation.potions-brewed", e.brewedPotions()));
    Loc loc = e.block().location().add(0.5D, 0.6D, 0.5D);
    fx(loc, FxPriority.TRANSITION)
        .dustBurst(Rgb.of(0xFFA500), 8, 0.3D, 1.2F)
        .particle(Particles.CAMPFIRE_COSY_SMOKE, 3, 0, 0.3D, 0, 0.2D, 0.02D)
        .chord(Sounds.ENTITY_GENERIC_EAT, 0.3F, 1.2F, Sounds.BLOCK_BREWING_STAND_BREW, 0.6F, 1.1F);
  }

  @ConfigDescription("Brew a Potion of Saturation from Regen Potion and Baked Potato.")
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
