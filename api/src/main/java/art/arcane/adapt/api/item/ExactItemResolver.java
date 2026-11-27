package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * The custom items a {@link ShapedRecipeParser} accepts by namespaced id, such as {@code golemancy:blank_core}, as an
 * ingredient or a result.
 */
@ApiStatus.Experimental
public interface ExactItemResolver {
  /** Knows no custom items, so recipes resolve vanilla materials and tags only. */
  ExactItemResolver NONE = new ExactItemResolver() {
    @Override
    public ExactIngredient ingredient(Id id) {
      return null;
    }

    @Override
    public ItemRef result(Id id) {
      return null;
    }
  };

  /** The ingredient the custom item {@code id} stands for, or null when this resolver does not know it. */
  ExactIngredient ingredient(Id id);

  /** A stack of the custom item {@code id} to craft or show, or null when this resolver does not know it. */
  ItemRef result(Id id);
}
