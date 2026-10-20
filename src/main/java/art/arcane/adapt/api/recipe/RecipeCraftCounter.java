package art.arcane.adapt.api.recipe;

import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeCraftCounter implements Listener {
  private final Set<NamespacedKey> tracked = ConcurrentHashMap.newKeySet();
  private final Map<UUID, OpenCount> openCounts = new ConcurrentHashMap<>();
  private volatile CraftObserver observer;

  public void track(NamespacedKey recipeKey) {
    tracked.add(Objects.requireNonNull(recipeKey));
  }

  public void observe(CraftObserver next) {
    observer = next;
  }

  public boolean tracks(Recipe recipe) {
    return recipe instanceof Keyed keyed && tracked.contains(keyed.getKey());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PrepareItemCraftEvent e) {
    if (!(e.getView().getPlayer() instanceof Player p)) {
      return;
    }

    OpenCount count = openCounts.get(p.getUniqueId());
    if (count != null) {
      collect(p, count, e.getInventory().getMatrix());
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(InventoryClickEvent e) {
    openCounts.remove(e.getWhoClicked().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void openCount(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    Recipe recipe = e.getRecipe();
    if (!tracks(recipe) || isExempt(p)) {
      return;
    }
    openCounts.put(p.getUniqueId(), OpenCount.open(recipe, e.getInventory().getMatrix(), p.getTicksLived()));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(InventoryDragEvent e) {
    openCounts.remove(e.getWhoClicked().getUniqueId());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerRecipeBookClickEvent e) {
    openCounts.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    openCounts.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    openCounts.remove(e.getPlayer().getUniqueId());
  }

  static int craftsStarted(int[] startAmounts, int[] remainingAmounts) {
    int started = 0;
    for (int slot = 0; slot < startAmounts.length; slot++) {
      if (startAmounts[slot] > 0) {
        started = Math.max(started, startAmounts[slot] - remainingAmounts[slot]);
      }
    }
    return started;
  }

  static boolean isCraftStep(int countedCrafts, int started) {
    return started >= 0 && started <= countedCrafts + 1;
  }

  private void collect(Player p, OpenCount count, ItemStack[] matrix) {
    int started = count.tick == p.getTicksLived() ? count.craftsStarted(matrix) : -1;
    if (!isCraftStep(count.countedCrafts, started)) {
      openCounts.remove(p.getUniqueId(), count);
      return;
    }

    int crafted = count.count(started);
    CraftObserver watching = observer;
    if (crafted > 0 && watching != null) {
      watching.crafted(p, count.recipe, crafted);
    }
  }

  private static boolean isExempt(Player p) {
    return p.getGameMode() == GameMode.CREATIVE;
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
  }

  public interface CraftObserver {
    void crafted(Player player, Recipe recipe, int crafts);
  }

  private static final class OpenCount {
    private final Recipe recipe;
    private final Material[] types;
    private final int[] amounts;
    private final int tick;
    private int countedCrafts;

    private OpenCount(Recipe recipe, Material[] types, int[] amounts, int tick) {
      this.recipe = recipe;
      this.types = types;
      this.amounts = amounts;
      this.tick = tick;
    }

    private static OpenCount open(Recipe recipe, ItemStack[] matrix, int tick) {
      Material[] types = new Material[matrix.length];
      int[] amounts = new int[matrix.length];
      for (int slot = 0; slot < matrix.length; slot++) {
        ItemStack stack = matrix[slot];
        if (!isEmpty(stack)) {
          types[slot] = stack.getType();
          amounts[slot] = stack.getAmount();
        }
      }
      return new OpenCount(recipe, types, amounts, tick);
    }

    private int craftsStarted(ItemStack[] matrix) {
      if (matrix.length != amounts.length) {
        return -1;
      }

      int[] remaining = new int[matrix.length];
      for (int slot = 0; slot < matrix.length; slot++) {
        ItemStack stack = matrix[slot];
        remaining[slot] = isEmpty(stack) || stack.getType() != types[slot] ? 0 : stack.getAmount();
      }
      return RecipeCraftCounter.craftsStarted(amounts, remaining);
    }

    private int count(int started) {
      if (started <= countedCrafts) {
        return 0;
      }

      int crafted = started - countedCrafts;
      countedCrafts = started;
      return crafted;
    }
  }
}
