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

public class BrewingNausea extends SimpleAdaptation<BrewingNausea.Config> {
  public static final Id ID = Id.adapt("brewing/nausea");
  public BrewingNausea() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crimson_fungus"));
    setInterval(1333);
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-nausea-1"), Id.minecraft("brown_mushroom"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("awkward")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.nausea.name").resolve())
            .setColor(Rgb.of(0x00FF00))
            .addEffect(Id.minecraft("nausea"), 600, 0, true, true, true)
            .build(), 320, 16));
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-nausea-2"), Id.minecraft("crimson_fungus"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("awkward")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.nausea.strong_name").resolve())
            .setColor(Rgb.of(0x00FF00))
            .addEffect(Id.minecraft("nausea"), 300, 1, true, true, true)
            .build(), 320, 32));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("poisonous_potato"))
        .key("challenge_brewing_nausea_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_nausea_25", "brewing.nausea.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.nausea.lore1").resolve());
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.nausea.lore2").resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!isBrewingRecipe(Id.parseAdapt(e.recipeId()))) {
      return;
    }
    AdaptApi.players().find(e.brewerId()).ifPresent((PlayerProfile profile) -> profile.addStat("brewing.nausea.potions-brewed", e.brewedPotions()));
    Loc loc = e.block().location().add(0.5D, 0.6D, 0.5D);
    timeline(loc)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.PORTAL, 0.45D + (0.15D * Math.sin(progress * Math.PI * 2.0D)), 1.0D, 8, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.3F, 0.7F, Sounds.BLOCK_BREWING_STAND_BREW, 0.6F, 0.8F);
          }
          if (tick == 4) {
            f.particle(Particles.WITCH, 2, 0, 0.5D, 0, 0.2D, 0.01D);
          }
        })
        .start();
  }

  @ConfigDescription("Brew a Potion of Nausea from Awkward Potion and Mushroom.")
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
