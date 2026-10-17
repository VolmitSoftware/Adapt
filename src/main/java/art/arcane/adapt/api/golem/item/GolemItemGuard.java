package art.arcane.adapt.api.golem.item;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecutterInventory;

import java.util.Optional;

public final class GolemItemGuard implements Listener {
  public static final String RECIPE_NAMESPACE = "adapt";
  public static final String RECIPE_PREFIX = "golemancy-";

  public static boolean isGolemancyRecipe(NamespacedKey key) {
    return key != null && RECIPE_NAMESPACE.equals(key.getNamespace()) && key.getKey().startsWith(RECIPE_PREFIX);
  }

  public static boolean blocksRecipe(NamespacedKey recipeKey, boolean golemItemIngredient) {
    return golemItemIngredient && !isGolemancyRecipe(recipeKey);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }
    Optional<GolemItemKind> kind = GolemItems.kindOf(e.getItem());
    if (kind.isPresent() && kind.get() != GolemItemKind.DORMANT_GOLEM) {
      e.setUseItemInHand(Event.Result.DENY);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPlaceEvent e) {
    if (GolemItems.isGolemItem(e.getItemInHand())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareItemCraftEvent e) {
    CraftingInventory inventory = e.getInventory();
    if (blocksRecipe(recipeKey(e.getRecipe()), containsGolemItem(inventory.getMatrix()))) {
      inventory.setResult(null);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(CrafterCraftEvent e) {
    NamespacedKey key = e.getRecipe().getKey();
    if (isGolemancyRecipe(key) || !(e.getBlock().getState() instanceof Container crafter)) {
      return;
    }
    if (blocksRecipe(key, containsGolemItem(crafter.getInventory().getContents()))) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareSmithingEvent e) {
    clearResultForGolemItems(e);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareAnvilEvent e) {
    clearResultForGolemItems(e);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareGrindstoneEvent e) {
    clearResultForGolemItems(e);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockCookEvent e) {
    if (GolemItems.isGolemItem(e.getSource())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    if (e.getSlotType() != InventoryType.SlotType.RESULT) {
      return;
    }
    Inventory top = e.getView().getTopInventory();
    if ((top instanceof StonecutterInventory || top instanceof MerchantInventory) && containsGolemItem(top.getContents())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDispenseEvent e) {
    if (GolemItems.isGolemItem(e.getItem())) {
      e.setCancelled(true);
    }
  }

  private static void clearResultForGolemItems(PrepareInventoryResultEvent e) {
    if (containsGolemItem(e.getInventory().getContents())) {
      e.setResult(null);
    }
  }

  private static boolean containsGolemItem(ItemStack[] items) {
    if (items == null) {
      return false;
    }
    for (ItemStack item : items) {
      if (GolemItems.isGolemItem(item)) {
        return true;
      }
    }
    return false;
  }

  private static NamespacedKey recipeKey(Recipe recipe) {
    return recipe instanceof Keyed keyed ? keyed.getKey() : null;
  }
}
