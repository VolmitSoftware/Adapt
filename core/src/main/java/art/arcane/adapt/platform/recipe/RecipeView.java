package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A server recipe as read back from the platform, Adapt's own and everyone else's. Shaped recipes list their rows in
 * {@code shape} with a choice per key character (characters without a choice are empty cells); shapeless recipes list
 * one choice per input. Other recipe kinds carry neither. {@code result} is the recipe's own output, which is empty
 * for special recipes that compute it from the grid.
 */
public record RecipeView(Id key, Kind kind, List<String> shape, Map<Character, RecipeChoice> shapedChoices,
                         List<RecipeChoice> choices, ItemRef result) {
  public RecipeView {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(result, "result");
    shape = List.copyOf(shape);
    shapedChoices = Map.copyOf(shapedChoices);
    choices = List.copyOf(choices);
  }

  public enum Kind {
    SHAPED,
    SHAPELESS,
    OTHER
  }
}
