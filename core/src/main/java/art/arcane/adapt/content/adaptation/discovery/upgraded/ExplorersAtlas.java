package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** Omniscience's Atlas: a book that opens the holder's private list of finds, pins and waypoints. */
public class ExplorersAtlas extends MutationTool<MutationTool.NoData> {
  static final String ID = "tool-discovery-explorers-atlas";
  private final Omniscience omniscience;

  public ExplorersAtlas(Omniscience owner) {
    super(ID, owner, Id.minecraft("book"), 1, NoData.class, DiscoveryMessages.ATLAS_NAME);
    omniscience = owner;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(new RecipeSpec.Shapeless(Id.adapt(ID), List.of(Ingredient.of(Id.minecraft("book")),
        Ingredient.of(Id.minecraft("map")), Ingredient.of(Id.minecraft("compass"))), create(new NoData()), 1));
  }

  @Override
  protected void use(ToolUse use) {
    if (use.rightClick()) {
      omniscience.openAtlas(use.player(), use.level());
    }
  }
}
