package com.example.geology;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

/** Crafting: unlocks the Geode Lens, a custom item that doubles the Prospect radius from the off hand. */
public final class GeodeLens extends SimpleAdaptation<GeodeLens.Config> {
  public static final Id ID = Id.of(GeologyAddon.NAMESPACE, "geology/geode_lens");
  public static final Id ITEM = Id.of(GeologyAddon.NAMESPACE, "geode_lens");
  public static final CustomItem.Definition<CustomItem.Plain> LENS = CustomItem.builder(ITEM, Id.minecraft("amethyst_shard"))
      .maxStackSize(1)
      .build();

  public GeodeLens() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("amethyst_shard"));
    registerItem(LENS);
    registerRecipe(RecipeSpec.shaped(Id.of(GeologyAddon.NAMESPACE, "geode_lens"), List.of(" A ", "AGA", " A "), Map.of('A', Ingredient.of(Id.minecraft("amethyst_shard")), 'G', Ingredient.of(Id.minecraft("glass_pane"))), LENS.create(1)));
  }

  public static boolean holds(PlayerRef player) {
    return LENS.is(player.inventory().offHand());
  }

  @Override
  public void addStats(int level, StatLines lines) {
    unlockLore(lines, text("lore1"));
  }

  public static final class Config extends AdaptationConfig {
    public Config() {
      baseCost = 4;
      initialCost = 4;
      costFactor = 0D;
      maxLevel = 1;
    }
  }
}
