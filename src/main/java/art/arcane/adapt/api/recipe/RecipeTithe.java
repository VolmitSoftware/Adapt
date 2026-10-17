package art.arcane.adapt.api.recipe;

import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeTithe implements Listener {
  private final Map<NamespacedKey, Integer> levelsByRecipe = new ConcurrentHashMap<>();
  private final Map<UUID, OpenCharge> openCharges = new ConcurrentHashMap<>();

  public static int affordableCrafts(int playerLevel, int levelsPerCraft) {
    if (levelsPerCraft <= 0) {
      return Integer.MAX_VALUE;
    }
    return Math.max(0, playerLevel) / levelsPerCraft;
  }

  public void set(NamespacedKey recipeKey, int levels) {
    Objects.requireNonNull(recipeKey);
    if (levels <= 0) {
      levelsByRecipe.remove(recipeKey);
      return;
    }
    levelsByRecipe.put(recipeKey, levels);
  }

  public int levels(Recipe recipe) {
    if (!(recipe instanceof Keyed keyed)) {
      return 0;
    }
    return levelsByRecipe.getOrDefault(keyed.getKey(), 0);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareItemCraftEvent e) {
    if (!(e.getView().getPlayer() instanceof Player p)) {
      return;
    }

    CraftingInventory inventory = e.getInventory();
    OpenCharge charge = openCharges.get(p.getUniqueId());
    if (charge != null) {
      collect(p, charge, inventory.getMatrix());
    }
    if (levelsByRecipe.isEmpty() || isExempt(p)) {
      return;
    }

    int levels = levels(e.getRecipe());
    if (levels > 0 && affordableCrafts(p.getLevel(), levels) < 1) {
      inventory.setResult(null);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(InventoryClickEvent e) {
    openCharges.remove(e.getWhoClicked().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    int levels = levels(e.getRecipe());
    if (levels > 0 && !isExempt(p) && affordableCrafts(p.getLevel(), levels) < 1) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void openCharge(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    int levels = levels(e.getRecipe());
    if (levels <= 0 || isExempt(p)) {
      return;
    }
    openCharges.put(p.getUniqueId(), OpenCharge.open(e.getInventory().getMatrix(), levels, p.getTicksLived()));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(InventoryDragEvent e) {
    openCharges.remove(e.getWhoClicked().getUniqueId());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerRecipeBookClickEvent e) {
    openCharges.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    openCharges.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    openCharges.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(CrafterCraftEvent e) {
    if (levels(e.getRecipe()) > 0) {
      e.setCancelled(true);
    }
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

  static boolean isCraftStep(int chargedCrafts, int started) {
    return started >= 0 && started <= chargedCrafts + 1;
  }

  private void collect(Player p, OpenCharge charge, ItemStack[] matrix) {
    int started = charge.tick == p.getTicksLived() ? charge.craftsStarted(matrix) : -1;
    if (!isCraftStep(charge.chargedCrafts, started)) {
      openCharges.remove(p.getUniqueId(), charge);
      return;
    }

    int due = charge.charge(started);
    if (due > 0) {
      p.setLevel(Math.max(0, p.getLevel() - due));
    }
  }

  private static boolean isExempt(Player p) {
    return p.getGameMode() == GameMode.CREATIVE;
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
  }

  private static final class OpenCharge {
    private final Material[] types;
    private final int[] amounts;
    private final int levelsPerCraft;
    private final int tick;
    private int chargedCrafts;

    private OpenCharge(Material[] types, int[] amounts, int levelsPerCraft, int tick) {
      this.types = types;
      this.amounts = amounts;
      this.levelsPerCraft = levelsPerCraft;
      this.tick = tick;
    }

    private static OpenCharge open(ItemStack[] matrix, int levelsPerCraft, int tick) {
      Material[] types = new Material[matrix.length];
      int[] amounts = new int[matrix.length];
      for (int slot = 0; slot < matrix.length; slot++) {
        ItemStack stack = matrix[slot];
        if (!isEmpty(stack)) {
          types[slot] = stack.getType();
          amounts[slot] = stack.getAmount();
        }
      }
      return new OpenCharge(types, amounts, levelsPerCraft, tick);
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
      return RecipeTithe.craftsStarted(amounts, remaining);
    }

    private int charge(int started) {
      if (started <= chargedCrafts) {
        return 0;
      }

      int due = (started - chargedCrafts) * levelsPerCraft;
      chargedCrafts = started;
      return due;
    }
  }
}
