package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;

/** The vanilla items a {@link ShapedRecipeParser} resolves names and tags against. */
@ApiStatus.Experimental
public interface RecipeItemCatalog {
  /** The item type for an upper-case legacy name such as {@code AMETHYST_SHARD}, or null when it is no item. */
  Id itemMaterial(String name);

  /** Items of an item tag, or null when the tag is unknown. */
  Set<Id> itemTag(Id key);

  /** A new stack of {@code type}. */
  ItemRef item(Id type, int amount);

  /** How many items of {@code type} one stack holds. */
  int maxStackSize(Id type);
}
