package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;

/**
 * A server recipe as read back from the platform, Adapt's own and everyone else's. Shaped recipes list their rows in
 * {@link #shape()} with a choice per key character (characters without a choice are empty cells); shapeless recipes
 * list one choice per input. Other recipe kinds carry neither. Adapt creates views; new accessors may be added.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface RecipeView {
  /** The recipe id. */
  Id key();

  /** The recipe shape. */
  Kind kind();

  /** Rows of a shaped recipe; empty otherwise. */
  List<String> shape();

  /** The choice for each key character of a shaped recipe; empty otherwise. */
  Map<Character, RecipeChoice> shapedChoices();

  /** One choice per input of a shapeless recipe; empty otherwise. */
  List<RecipeChoice> choices();

  /** The recipe's own output, which is empty for special recipes that compute it from the grid. */
  ItemRef result();

  /**
   * The recipe shape: shaped and shapeless crafting carry choices, {@code OTHER} covers every other kind.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  enum Kind {
    SHAPED,
    SHAPELESS,
    OTHER
  }
}
