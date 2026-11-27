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

public class HerbalismCraftableMushroomBlocks extends SimpleAdaptation<HerbalismCraftableMushroomBlocks.Config> {
  public static final Id ID = Id.adapt("herbalism/mushroom_blocks");
  private static final String RED_BLOCK_KEY = "herbalism-redmushblock";
  private static final String BROWN_BLOCK_KEY = "herbalism-brownmushblock";

  public HerbalismCraftableMushroomBlocks() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("brown_mushroom_block"));
    setInterval(17772);
    ItemOps items = AdaptApi.platform().host().items();
    registerRecipe(RecipeSpec.shaped(Id.adapt(RED_BLOCK_KEY), List.of(
            "II",
            "II"), Map.of('I', Ingredient.of(Id.minecraft("red_mushroom"))), items.create(Id.minecraft("red_mushroom_block"), 1)));
    registerRecipe(RecipeSpec.shaped(Id.adapt(BROWN_BLOCK_KEY), List.of(
            "II",
            "II"), Map.of('I', Ingredient.of(Id.minecraft("brown_mushroom"))), items.create(Id.minecraft("brown_mushroom_block"), 1)));
    registerRecipe(RecipeSpec.shapeless(Id.adapt("herbalism-mushstemred"), List.of(Ingredient.of(Id.minecraft("red_mushroom_block"))), items.create(Id.minecraft("mushroom_stem"), 1)));
    registerRecipe(RecipeSpec.shapeless(Id.adapt("herbalism-mushstembrown"), List.of(Ingredient.of(Id.minecraft("brown_mushroom_block"))), items.create(Id.minecraft("mushroom_stem"), 1)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("red_mushroom_block"))
        .key("challenge_herbalism_mushroom_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_mushroom_100", "herbalism.mushroom-blocks.crafted", 100, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.herbalism.mushroom_blocks.lore1").resolve());
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (e.recipeKind() == CraftItemEvent.RecipeKind.SHAPED && (Id.adapt(RED_BLOCK_KEY).equals(e.recipeKey()) || Id.adapt(BROWN_BLOCK_KEY).equals(e.recipeKey()))) {
      addStat(p, "herbalism.mushroom-blocks.crafted", 1);
      if (statOf(p, "herbalism.mushroom-blocks.crafted") == 1) {
        fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
            .column(Particles.END_ROD, 6, 1.4D)
            .particle(Particles.SPORE_BLOSSOM_AIR, 6, 0, 0.6D, 0, 0.45D, 0.02D)
            .chord(Sounds.BLOCK_FUNGUS_PLACE, 0.6F, 0.9F, Sounds.ENTITY_PLAYER_LEVELUP, 0.4F, 1.5F);
      }
    }
  }


  @ConfigDescription("Craft Mushroom Blocks from Mushrooms in a Crafting Table.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 1;
      baseCost = 1;
      permanent = true;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
