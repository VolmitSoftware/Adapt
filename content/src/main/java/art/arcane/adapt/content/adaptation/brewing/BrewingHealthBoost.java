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

public class BrewingHealthBoost extends SimpleAdaptation<BrewingHealthBoost.Config> {
  public static final Id ID = Id.adapt("brewing/healthboost");
  public BrewingHealthBoost() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.brewing.health_boost");
    setIcon(Id.minecraft("enchanted_golden_apple"));
    setInterval(1330);
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-healthboost-1"), Id.minecraft("golden_apple"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("healing")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.health_boost.name").resolve())
            .setColor(Rgb.of(0xFF0000))
            .addEffect(Id.minecraft("health_boost"), 1200, 0, true, true, true)
            .build(), 320, 16));
    registerBrewingRecipe(new BrewingRecipe(Id.adapt("brewing-healthboost-2"), Id.minecraft("enchanted_golden_apple"), Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("healing")), Potions.of(Potions.Type.REGULAR)
            .setName(Text.key("adapt.brewing.health_boost.strong_name").resolve())
            .setColor(Rgb.of(0xFF0000))
            .addEffect(Id.minecraft("health_boost"), 1200, 1, true, true, true)
            .build(), 320, 32));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("glistering_melon_slice"))
        .key("challenge_brewing_health_boost_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_brewing_health_boost_25", "brewing.health-boost.potions-brewed", 25, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.health_boost.lore1").resolve());
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.brewing.health_boost.lore2").resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptBrewCompleteEvent e) {
    if (!isBrewingRecipe(Id.parseAdapt(e.recipeId()))) {
      return;
    }
    AdaptApi.players().find(e.brewerId()).ifPresent((PlayerProfile profile) -> profile.addStat("brewing.health-boost.potions-brewed", e.brewedPotions()));
    Loc loc = e.block().location().add(0.5D, 0.6D, 0.5D);
    timeline(loc)
        .duration(4)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24.0D)
        .frame((f, tick, progress) -> {
          f.dustRing(Rgb.of(0xFF0000), 0.4D + (0.4D * progress), 8, 1.0F);
          if (tick == 0) {
            f.particle(Particles.HEART, 4, 0, 0.4D, 0, 0.25D, 0.02D)
                .chord(Sounds.ENTITY_PLAYER_LEVELUP, 0.25F, 1.8F, Sounds.BLOCK_BREWING_STAND_BREW, 0.6F, 1.1F);
          }
        })
        .start();
  }

  @ConfigDescription("Brew a Potion of Health Boost from Instant Heal and Golden Apple.")
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
