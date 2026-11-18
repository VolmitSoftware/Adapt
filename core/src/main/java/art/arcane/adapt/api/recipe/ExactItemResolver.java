package art.arcane.adapt.api.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

public interface ExactItemResolver {
  ExactIngredient ingredient(Id id);

  ItemRef result(Id id);
}
