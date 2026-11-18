package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.platform.item.ItemRef;

@FunctionalInterface
public interface InfusionIngredient {
  boolean matches(ItemRef item);
}
