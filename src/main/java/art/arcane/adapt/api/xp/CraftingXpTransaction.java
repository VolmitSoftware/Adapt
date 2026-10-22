package art.arcane.adapt.api.xp;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class CraftingXpTransaction {
  private final CraftItemEvent event;
  private final ItemStack result;
  private final String rewardKey;
  private int committedItems;

  private CraftingXpTransaction(CraftItemEvent event, ItemStack result, String rewardKey) {
    this.event = event;
    this.result = result;
    this.rewardKey = rewardKey;
  }

  public static CraftingXpTransaction open(CraftItemEvent event) {
    if (event.isCancelled() || event.getAction() == InventoryAction.NOTHING
        || event.getAction() == InventoryAction.CLONE_STACK) {
      return null;
    }
    ItemStack result = event.getInventory().getResult();
    if (result == null || result.getType().isAir() || result.getAmount() <= 0
        || isResourceConversion(event.getInventory().getMatrix(), result.getType())) {
      return null;
    }
    String recipe = event.getRecipe() instanceof Keyed keyed
        ? keyed.getKey().toString() : result.getType().name().toLowerCase(Locale.ROOT);
    return new CraftingXpTransaction(event, result.clone(), "crafting:recipe:" + recipe);
  }

  public void accept(ItemStack crafted) {
    if (!event.isCancelled() && crafted != null && crafted.getAmount() > 0 && result.isSimilar(crafted)) {
      committedItems = (int) Math.min(Integer.MAX_VALUE, (long) committedItems + crafted.getAmount());
    }
  }

  public int committedItems() {
    return event.isCancelled() ? 0 : committedItems;
  }

  public Material material() {
    return result.getType();
  }

  public String rewardKey() {
    return rewardKey;
  }

  public static boolean isResourceConversion(ItemStack[] matrix, Material result) {
    String family = resourceFamily(result);
    if (family == null || matrix == null) {
      return false;
    }
    boolean ingredient = false;
    for (ItemStack item : matrix) {
      if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
        continue;
      }
      if (family.equals("honey") && item.getType() == Material.GLASS_BOTTLE) {
        continue;
      }
      if (!family.equals(resourceFamily(item.getType()))) {
        return false;
      }
      ingredient = true;
    }
    return ingredient;
  }

  private static String resourceFamily(Material material) {
    return switch (material.name()) {
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
