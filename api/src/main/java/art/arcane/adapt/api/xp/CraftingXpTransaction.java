package art.arcane.adapt.api.xp;

import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Locale;

/**
 * One craft that may pay crafting XP: open it when the craft starts, feed it what the crafting actually produced, and
 * read how many items were made. Crafts that only pack or unpack a resource (ingots to blocks and back) never open.
 */
@ApiStatus.Experimental
public final class CraftingXpTransaction {
  private static final Id GLASS_BOTTLE = Id.minecraft("glass_bottle");

  private final CraftItemEvent event;
  private final ItemRef result;
  private final String recipe;
  private int committedItems;

  private CraftingXpTransaction(CraftItemEvent event, ItemRef result, String recipe) {
    this.event = event;
    this.result = result;
    this.recipe = recipe;
  }

  /** The transaction of {@code event}, or null for a cancelled, empty, cloning or resource-packing craft. */
  public static CraftingXpTransaction open(CraftItemEvent event) {
    if (event.isCancelled() || event.action() == InventoryAction.NOTHING
        || event.action() == InventoryAction.CLONE_STACK) {
      return null;
    }
    ItemRef result = event.currentItem();
    if (isEmpty(result) || isResourceConversion(event.matrix(), result.type())) {
      return null;
    }
    String recipe = event.recipeKey() != null
        ? event.recipeKey().toString() : result.type().path().toLowerCase(Locale.ROOT);
    return new CraftingXpTransaction(event, result.copy(), recipe);
  }

  /** Counts {@code crafted} as made by this craft when it is the craft's result. */
  public void accept(ItemRef crafted) {
    if (!event.isCancelled() && crafted != null && crafted.amount() > 0 && result.isSimilar(crafted)) {
      committedItems = (int) Math.min(Integer.MAX_VALUE, (long) committedItems + crafted.amount());
    }
  }

  /** The items made so far; 0 once the event was cancelled. */
  public int committedItems() {
    return event.isCancelled() ? 0 : committedItems;
  }

  /** The result's item type. */
  public Id material() {
    return result.type();
  }

  /** The recipe key, or the result item's path when the recipe has none. */
  public String recipe() {
    return recipe;
  }

  /** Whether the grid only packs or unpacks one resource family into {@code result}, such as nuggets into an ingot. */
  public static boolean isResourceConversion(List<ItemRef> matrix, Id result) {
    String family = resourceFamily(result);
    if (family == null || matrix == null) {
      return false;
    }
    boolean ingredient = false;
    for (ItemRef item : matrix) {
      if (isEmpty(item)) {
        continue;
      }
      if (family.equals("honey") && item.type().equals(GLASS_BOTTLE)) {
        continue;
      }
      if (!family.equals(resourceFamily(item.type()))) {
        return false;
      }
      ingredient = true;
    }
    return ingredient;
  }

  private static boolean isEmpty(ItemRef item) {
    return item == null || item.isEmpty() || item.type().isAir() || item.amount() <= 0;
  }

  private static String resourceFamily(Id material) {
    if (material == null || !material.isMinecraft()) {
      return null;
    }
    return switch (material.path().toUpperCase(Locale.ROOT)) {
      case "IRON_NUGGET", "IRON_INGOT", "IRON_BLOCK" -> "iron";
      case "GOLD_NUGGET", "GOLD_INGOT", "GOLD_BLOCK" -> "gold";
      case "COPPER_NUGGET", "COPPER_INGOT", "COPPER_BLOCK" -> "copper";
      case "NETHERITE_INGOT", "NETHERITE_BLOCK" -> "netherite";
      case "RAW_IRON", "RAW_IRON_BLOCK" -> "raw-iron";
      case "RAW_GOLD", "RAW_GOLD_BLOCK" -> "raw-gold";
      case "RAW_COPPER", "RAW_COPPER_BLOCK" -> "raw-copper";
      case "DIAMOND", "DIAMOND_BLOCK" -> "diamond";
      case "EMERALD", "EMERALD_BLOCK" -> "emerald";
      case "LAPIS_LAZULI", "LAPIS_BLOCK" -> "lapis";
      case "REDSTONE", "REDSTONE_BLOCK" -> "redstone";
      case "COAL", "COAL_BLOCK" -> "coal";
      case "WHEAT", "HAY_BLOCK" -> "wheat";
      case "DRIED_KELP", "DRIED_KELP_BLOCK" -> "kelp";
      case "SLIME_BALL", "SLIME_BLOCK" -> "slime";
      case "HONEY_BOTTLE", "HONEY_BLOCK" -> "honey";
      case "BONE_MEAL", "BONE_BLOCK" -> "bone-meal";
      default -> null;
    };
  }
}
