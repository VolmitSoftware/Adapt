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

public class BrewingDecay extends SimpleAdaptation<BrewingDecay.Config> {
  public static final Id ID = Id.adapt("brewing/decay");
  public BrewingDecay() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wither_rose"));
    setInterval(1334);
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-decay-1"), Id.minecraft("poisonous_potato"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("weakness")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.decay.name").resolve())
            .setColor(Rgb.of(0x800000))
            .addEffect(Id.minecraft("wither"), 320, 0, true, true, true)
            .build(), 320, 16));
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-decay-2"), Id.minecraft("crimson_roots"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("weakness")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.decay.strong_name").resolve())
            .setColor(Rgb.of(0x800000))
            .addEffect(Id.minecraft("wither"), 160, 1, true, true, true)
            .build(), 320, 32));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_rose"))
        .key("challenge_brewing_decay_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_decay_25", "brewing.decay.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.decay.lore1").resolve());
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.decay.lore2").resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!isBrewingRecipe(Id.parseAdapt(e.recipeId()))) {
      return;
    }
    AdaptApi.players().find(e.brewerId()).ifPresent((PlayerProfile profile) -> profile.addStat("brewing.decay.potions-brewed", e.brewedPotions()));
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
