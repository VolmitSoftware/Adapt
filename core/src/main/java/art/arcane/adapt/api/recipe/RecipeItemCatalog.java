package art.arcane.adapt.api.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;

public interface RecipeItemCatalog {
  /** The item type for an upper-case legacy name such as {@code AMETHYST_SHARD}, or null when it is no item. */
  Id itemMaterial(String name);

  /** Items of an item tag, or null when the tag is unknown. */
  Set<Id> itemTag(Id key);

  ItemRef item(Id type, int amount);

  int maxStackSize(Id type);
}
