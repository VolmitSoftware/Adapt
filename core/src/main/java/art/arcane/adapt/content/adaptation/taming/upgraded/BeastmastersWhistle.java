package art.arcane.adapt.content.adaptation.taming.upgraded;

import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.localization.catalog.TamingMessages;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/**
 * One Mind's whistle: right-click sends your pets at what you look at, sneak-right-click recalls them and calls your
 * bonded mount, left-click opens its menu. Made from a goat horn or a bone with string and an iron nugget.
 */
public class BeastmastersWhistle extends MutationTool<MutationTool.NoData> {
  public static final String ID = "tool-taming-whistle";
  public static final Id MATERIAL = Id.minecraft("goat_horn");

  private final OneMind owner;

  public BeastmastersWhistle(OneMind owner) {
    super(ID, owner, MATERIAL, 1, NoData.class, TamingMessages.WHISTLE_NAME, TamingMessages.WHISTLE_LORE1,
        TamingMessages.WHISTLE_LORE2, TamingMessages.WHISTLE_LORE3);
    this.owner = owner;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(
        new RecipeSpec.Shapeless(Id.adapt(ID), List.of(Ingredient.of(MATERIAL), Ingredient.of(Id.minecraft("string")),
            Ingredient.of(Id.minecraft("iron_nugget"))), create(new NoData()), 1),
        new RecipeSpec.Shapeless(Id.adapt(ID + "-bone"), List.of(Ingredient.of(Id.minecraft("bone")),
            Ingredient.of(Id.minecraft("string")), Ingredient.of(Id.minecraft("iron_nugget"))), create(new NoData()), 1));
  }

  @Override
  protected void use(ToolUse use) {
    owner.whistle(use);
  }
}
