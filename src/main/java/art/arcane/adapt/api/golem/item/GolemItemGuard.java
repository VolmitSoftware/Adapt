package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.api.recipe.ExactIngredientBook;
import org.bukkit.Keyed;
import org.bukkit.Material;
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
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
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
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecutterInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GolemItemGuard implements Listener {
  public static final String RECIPE_NAMESPACE = "adapt";
  public static final String RECIPE_PREFIX = "golemancy-";
  private static final int SMITHING_INPUTS = 3;
  private static final int ANVIL_INPUTS = 2;
  private static final int GRINDSTONE_INPUTS = 2;

  private final ExactIngredientBook recipes;

  public GolemItemGuard(ExactIngredientBook recipes) {
    this.recipes = Objects.requireNonNull(recipes);
  }

  public static boolean isGolemancyRecipe(NamespacedKey key) {
    return key != null && RECIPE_NAMESPACE.equals(key.getNamespace()) && key.getKey().startsWith(RECIPE_PREFIX);
  }

  public boolean blocksRecipe(NamespacedKey recipeKey, List<ItemStack> golemItems) {
    if (!isGolemancyRecipe(recipeKey)) {
      return !golemItems.isEmpty();
    }
    return !recipes.satisfiedBy(recipeKey, golemItems);
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
    Optional<GolemItemKind> kind = GolemItems.kindOf(e.getItemInHand());
    if (kind.isPresent() && kind.get() != GolemItemKind.DORMANT_GOLEM) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void placed(BlockPlaceEvent e) {
    if (GolemItems.kindOf(e.getItemInHand()).filter(kind -> kind == GolemItemKind.DORMANT_GOLEM).isPresent()) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareItemCraftEvent e) {
    CraftingInventory inventory = e.getInventory();
    if (blocksRecipe(recipeKey(e.getRecipe()), golemItems(inventory.getMatrix()))) {
      inventory.setResult(null);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(CrafterCraftEvent e) {
    if (e.getBlock().getState() instanceof Container crafter
        && blocksRecipe(e.getRecipe().getKey(), golemItems(crafter.getInventory().getContents()))) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareSmithingEvent e) {
    clearResultForGolemInputs(e, SMITHING_INPUTS);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareAnvilEvent e) {
    clearResultForGolemInputs(e, ANVIL_INPUTS);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareGrindstoneEvent e) {
    clearResultForGolemInputs(e, GRINDSTONE_INPUTS);
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
    if (top instanceof StonecutterInventory stonecutter && GolemItems.isGolemItem(stonecutter.getInputItem())) {
      e.setCancelled(true);
    } else if (top instanceof MerchantInventory merchant && tradeSwallowsGolemItem(merchant)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDispenseEvent e) {
    if (e.getBlock().getType() != Material.DROPPER && GolemItems.isGolemItem(e.getItem())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(InventoryPickupItemEvent e) {
    ItemStack item = e.getItem().getItemStack();
    Material type = item.getType();
    if (type != Material.FIREWORK_STAR && !GolemItems.isDormantBlock(type)) {
      return;
    }
    if (GolemItems.kindOf(item).filter(GolemItemGuard::ownerBound).isPresent()) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareItemEnchantEvent e) {
    if (GolemItems.isGolemItem(e.getItem())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EnchantItemEvent e) {
    if (GolemItems.isGolemItem(e.getItem())) {
      e.setCancelled(true);
    }
  }

  private static boolean ownerBound(GolemItemKind kind) {
    return kind == GolemItemKind.SHATTERED_CORE || kind == GolemItemKind.DORMANT_GOLEM;
  }

  private static void clearResultForGolemInputs(PrepareInventoryResultEvent e, int inputSlots) {
    Inventory inventory = e.getInventory();
    for (int slot = 0; slot < inputSlots; slot++) {
      if (GolemItems.isGolemItem(inventory.getItem(slot))) {
        e.setResult(null);
        return;
      }
    }
  }

  private static List<ItemStack> golemItems(ItemStack[] items) {
    List<ItemStack> golemItems = new ArrayList<>();
    if (items == null) {
      return golemItems;
    }
    for (ItemStack item : items) {
      if (GolemItems.isGolemItem(item)) {
        golemItems.add(item);
      }
    }
    return golemItems;
  }

  private static boolean tradeSwallowsGolemItem(MerchantInventory merchant) {
    MerchantRecipe trade = merchant.getSelectedRecipe();
    List<ItemStack> tradeIngredients = trade == null ? List.of() : trade.getIngredients();
    return golemItems(new ItemStack[]{merchant.getItem(0), merchant.getItem(1)}).size()
        > golemItems(tradeIngredients.toArray(new ItemStack[0])).size();
  }

  private static NamespacedKey recipeKey(Recipe recipe) {
    return recipe instanceof Keyed keyed ? keyed.getKey() : null;
  }
}
