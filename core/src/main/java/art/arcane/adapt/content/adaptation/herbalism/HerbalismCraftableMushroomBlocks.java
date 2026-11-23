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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;

public class HerbalismCraftableMushroomBlocks extends SimpleAdaptation<HerbalismCraftableMushroomBlocks.Config> {
  private static final String RED_BLOCK_KEY = "herbalism-redmushblock";
  private static final String BROWN_BLOCK_KEY = "herbalism-brownmushblock";

  public HerbalismCraftableMushroomBlocks() {
    super("herbalism-mushroom-blocks");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("brown_mushroom_block"));
    setInterval(17772);
    ItemOps items = AdaptCore.platform().host().items();
    registerRecipe(new RecipeSpec.Shaped(Id.adapt(RED_BLOCK_KEY),
        List.of(
            "II",
            "II"),
        Map.of('I', Ingredient.of(Id.minecraft("red_mushroom"))),
        items.create(Id.minecraft("red_mushroom_block"), 1)));
    registerRecipe(new RecipeSpec.Shaped(Id.adapt(BROWN_BLOCK_KEY),
        List.of(
            "II",
            "II"),
        Map.of('I', Ingredient.of(Id.minecraft("brown_mushroom"))),
        items.create(Id.minecraft("brown_mushroom_block"), 1)));
    registerRecipe(new RecipeSpec.Shapeless(Id.adapt("herbalism-mushstemred"),
        List.of(Ingredient.of(Id.minecraft("red_mushroom_block"))),
        items.create(Id.minecraft("mushroom_stem"), 1)));
    registerRecipe(new RecipeSpec.Shapeless(Id.adapt("herbalism-mushstembrown"),
        List.of(Ingredient.of(Id.minecraft("brown_mushroom_block"))),
        items.create(Id.minecraft("mushroom_stem"), 1)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("red_mushroom_block"))
        .key("challenge_herbalism_mushroom_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_mushroom_100", "herbalism.mushroom-blocks.crafted", 100, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(HerbalismMessages.MUSHROOM_BLOCKS_LORE1));
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (e.recipeKind() == CraftItemEvent.RecipeKind.SHAPED && (Id.adapt(RED_BLOCK_KEY).equals(e.recipeKey()) || Id.adapt(BROWN_BLOCK_KEY).equals(e.recipeKey()))) {
      addStat(p, "herbalism.mushroom-blocks.crafted", 1);
      if (getPlayer(p).getData().getStat("herbalism.mushroom-blocks.crafted") == 1) {
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
