package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/**
 * A mutation tool the adaptation {@code adaptationId} hands out: its id, plain localized name, icon, the level of that
 * adaptation its recipe unlocks at and the ingredients of its first recipe. An ingredient names an item id, or a tag as
 * {@code #namespace:path}.
 */
public record ToolView(String adaptationId, String toolId, String name, IconRef icon, int requiredLevel,
                       List<Ingredient> ingredients) {
  public ToolView {
    Objects.requireNonNull(adaptationId, "adaptationId");
    Objects.requireNonNull(toolId, "toolId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(icon, "icon");
    ingredients = List.copyOf(ingredients);
  }

  /** {@code count} of the item or tag {@code itemId}. */
  public record Ingredient(String itemId, int count) {
    public Ingredient {
      Objects.requireNonNull(itemId, "itemId");
    }
  }
}
