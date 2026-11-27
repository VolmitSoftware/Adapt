package art.arcane.adapt.engine.platform;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EngineRecipeView(Id key, Kind kind, List<String> shape, Map<Character, RecipeChoice> shapedChoices,
                               List<RecipeChoice> choices, ItemRef result) implements RecipeView {
  public EngineRecipeView {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(result, "result");
    shape = List.copyOf(shape);
    shapedChoices = Map.copyOf(shapedChoices);
    choices = List.copyOf(choices);
  }
}
