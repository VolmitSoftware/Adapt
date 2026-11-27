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

public class BrewingDarkness extends SimpleAdaptation<BrewingDarkness.Config> {
  public static final Id ID = Id.adapt("brewing/darkness");
  public BrewingDarkness() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("black_concrete"));
    setInterval(1335);
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-darkness"), Id.minecraft("black_concrete"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("night_vision")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.darkness.name").resolve())
            .setColor(Rgb.of(0x000000))
            .addEffect(Id.minecraft("darkness"), 600, 0, true, true, true)
            .build(), 320, 16));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("brewing_stand"))
        .key("challenge_brewing_darkness_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_darkness_25", "brewing.darkness.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.darkness.lore1").resolve());
    v.addLore(TextFormat.GRAY + "- " + Text.key("adapt.brewing.darkness.lore2").resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!isBrewingRecipe(Id.parseAdapt(e.recipeId()))) {
      return;
    }
    AdaptApi.players().find(e.brewerId()).ifPresent((PlayerProfile profile) -> profile.addStat("brewing.darkness.potions-brewed", e.brewedPotions()));
    Loc loc = e.block().location().add(0.5D, 0.6D, 0.5D);
    timeline(loc)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particles.SCULK_SOUL, 1.0D - (0.8D * progress), 12, 0.4D);
          if (tick == 0) {
            f.chord(Sounds.BLOCK_SCULK_CATALYST_BREAK, 0.5F, 1.0F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.5F);
          }
          if (tick == 5) {
            f.sound(Sounds.BLOCK_BREWING_STAND_BREW, 0.6F, 0.7F);
          }
        })
        .start();
  }

  @ConfigDescription("Brew a Potion of Darkness from NightVision Potion and Black Concrete.")
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
