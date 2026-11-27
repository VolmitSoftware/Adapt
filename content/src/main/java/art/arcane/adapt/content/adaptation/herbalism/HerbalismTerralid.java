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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

public class HerbalismTerralid extends SimpleAdaptation<HerbalismTerralid.Config> {
  public static final Id ID = Id.adapt("herbalism/terralid");
  private static final String RECIPE_KEY = "herbalism-dirt-terralid";

  public HerbalismTerralid() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("grass_block"));
    setInterval(17771);
    ItemOps items = AdaptApi.platform().host().items();
    registerRecipe(RecipeSpec.shaped(Id.adapt(RECIPE_KEY), List.of(
            "SSS",
            "DDD"), Map.of('S', Ingredient.of(Id.minecraft("wheat_seeds")), 'D', Ingredient.of(Id.minecraft("dirt"))), items.create(Id.minecraft("grass_block"), 3)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("grass_block"))
        .key("challenge_herbalism_terralid_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_terralid_200", "herbalism.terralid.grass-crafted", 200, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.herbalism.terralid.lore1").resolve());
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (e.recipeKind() == CraftItemEvent.RecipeKind.SHAPED && Id.adapt(RECIPE_KEY).equals(e.recipeKey())) {
      addStat(p, "herbalism.terralid.grass-crafted", 1);
      if (statOf(p, "herbalism.terralid.grass-crafted") == 1) {
        fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
            .column(Particles.END_ROD, 6, 1.4D)
            .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.6D, 0, 0.4D, 0.04D)
            .chord(Sounds.BLOCK_GRASS_BREAK, 0.6F, 1.0F, Sounds.ENTITY_PLAYER_LEVELUP, 0.4F, 1.5F);
      }
    }
  }


  @ConfigDescription("Craft Grass Blocks from Seeds and Dirt.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      permanent = true;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 1;
    }
  }
}
