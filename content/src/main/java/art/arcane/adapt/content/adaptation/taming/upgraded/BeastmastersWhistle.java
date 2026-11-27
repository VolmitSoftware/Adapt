package art.arcane.adapt.content.adaptation.taming.upgraded;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/**
 * One Mind's whistle: right-click sends your pets at what you look at, sneak-right-click recalls them and calls your
 * bonded mount, left-click opens its menu. Made from a goat horn or a bone with string and an iron nugget.
 */
public class BeastmastersWhistle extends MutationTool<CustomItem.Plain> {
  public static final Id ID = Id.adapt("taming/tool/whistle");
  public static final Id MATERIAL = Id.minecraft("goat_horn");

  private final OneMind owner;

  public BeastmastersWhistle(OneMind owner) {
    super(ToolItems.plain(ID, MATERIAL).build(), owner, 1);
    this.owner = owner;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(
        RecipeSpec.shapeless(ID, List.of(Ingredient.of(MATERIAL), Ingredient.of(Id.minecraft("string")),
            Ingredient.of(Id.minecraft("iron_nugget"))), create(CustomItem.Plain.INSTANCE)).withRequiredLevel(1),
        RecipeSpec.shapeless(ID.withPath(ID.path() + "/bone"), List.of(Ingredient.of(Id.minecraft("bone")),
            Ingredient.of(Id.minecraft("string")), Ingredient.of(Id.minecraft("iron_nugget"))), create(CustomItem.Plain.INSTANCE)).withRequiredLevel(1));
  }

  @Override
  public void use(ToolUse use) {
    owner.whistle(use);
  }
}
