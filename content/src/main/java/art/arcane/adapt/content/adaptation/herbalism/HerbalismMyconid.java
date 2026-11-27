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

public class HerbalismMyconid extends SimpleAdaptation<HerbalismMyconid.Config> {
  public static final Id ID = Id.adapt("herbalism/myconid");
  private static final String RECIPE_KEY = "herbalism-dirt-myconid";

  public HerbalismMyconid() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("mycelium"));
    setInterval(17771);
    ItemOps items = AdaptApi.platform().host().items();
    registerRecipe(RecipeSpec.shapeless(Id.adapt(RECIPE_KEY), List.of(Ingredient.of(Id.minecraft("dirt")), Ingredient.of(Id.minecraft("red_mushroom")), Ingredient.of(Id.minecraft("brown_mushroom"))), items.create(Id.minecraft("mycelium"), 1)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("mycelium"))
        .key("challenge_herbalism_myconid_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_myconid_100", "herbalism.myconid.mycelium-crafted", 100, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.herbalism.myconid.lore1").resolve());
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (e.recipeKind() == CraftItemEvent.RecipeKind.SHAPELESS && Id.adapt(RECIPE_KEY).equals(e.recipeKey())) {
      addStat(p, "herbalism.myconid.mycelium-crafted", 1);
      if (statOf(p, "herbalism.myconid.mycelium-crafted") == 1) {
        fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
            .column(Particles.END_ROD, 6, 1.4D)
            .particle(Particles.SPORE_BLOSSOM_AIR, 6, 0, 0.6D, 0, 0.45D, 0.02D)
            .particle(Particles.CRIMSON_SPORE, 4, 0, 0.5D, 0, 0.4D, 0.02D)
            .chord(Sounds.BLOCK_FUNGUS_PLACE, 0.6F, 0.8F, Sounds.ENTITY_PLAYER_LEVELUP, 0.4F, 1.5F);
      }
    }
  }


  @ConfigDescription("Craft Mycelium from Dirt and Mushrooms.")
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
