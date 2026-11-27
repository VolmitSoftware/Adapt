package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** Omniscience's Atlas: a book that opens the holder's private list of finds, pins and waypoints. */
public class ExplorersAtlas extends MutationTool<CustomItem.Plain> {
  static final Id ID = Id.adapt("discovery/tool/explorers_atlas");
  private final Omniscience omniscience;

  public ExplorersAtlas(Omniscience owner) {
    super(ToolItems.plain(ID, Id.minecraft("book")).build(), owner, 1);
    omniscience = owner;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shapeless(ID, List.of(Ingredient.of(Id.minecraft("book")),
        Ingredient.of(Id.minecraft("map")), Ingredient.of(Id.minecraft("compass"))), create(CustomItem.Plain.INSTANCE)).withRequiredLevel(1));
  }

  @Override
  public void use(ToolUse use) {
    if (use.rightClick()) {
      omniscience.openAtlas(use.player(), use.level());
    }
  }
}
