package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.inventory.InventoryDragEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRecipeBookClickEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeCraftCounter {
  private final Set<Id> tracked = ConcurrentHashMap.newKeySet();
  private final Map<UUID, OpenCount> openCounts = new ConcurrentHashMap<>();
  private volatile CraftObserver observer;

  public void track(Id recipeKey) {
    tracked.add(Objects.requireNonNull(recipeKey));
  }

  public void observe(CraftObserver next) {
    observer = next;
  }

  public boolean tracks(Id recipeKey) {
    return recipeKey != null && tracked.contains(recipeKey);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PrepareItemCraftEvent e) {
    PlayerRef p = e.player();
    if (p == null) {
      return;
    }

    OpenCount count = openCounts.get(p.uuid());
    if (count != null) {
      collect(p, count, e.matrix());
    }
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(InventoryClickEvent e) {
    openCounts.remove(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void openCount(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (p == null) {
      return;
    }

    Id recipe = e.recipeKey();
    if (!tracks(recipe) || isExempt(p)) {
      return;
    }
    openCounts.put(p.uuid(), OpenCount.open(recipe, e.currentItem(), e.matrix(), p.ticksLived()));
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(InventoryDragEvent e) {
    openCounts.remove(e.player().uuid());
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(PlayerRecipeBookClickEvent e) {
    openCounts.remove(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(InventoryCloseEvent e) {
    openCounts.remove(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    openCounts.remove(e.player().uuid());
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

  private void collect(PlayerRef p, OpenCount count, List<ItemRef> matrix) {
    int started = count.tick == p.ticksLived() ? count.craftsStarted(matrix) : -1;
    if (!isCraftStep(count.countedCrafts, started)) {
      openCounts.remove(p.uuid(), count);
      return;
    }

    int crafted = count.count(started);
    CraftObserver watching = observer;
    if (crafted > 0 && watching != null) {
      watching.crafted(p, count.recipe, count.result, crafted);
    }
  }

  private static boolean isExempt(PlayerRef p) {
    return p.gameMode() == GameMode.CREATIVE;
  }

  private static boolean isEmpty(ItemRef stack) {
    return stack == null || stack.isEmpty() || stack.type().isAir() || stack.amount() <= 0;
  }

  public interface CraftObserver {
    void crafted(PlayerRef player, Id recipe, ItemRef result, int crafts);
  }

  private static final class OpenCount {
    private final Id recipe;
    private final ItemRef result;
    private final Id[] types;
    private final int[] amounts;
    private final int tick;
    private int countedCrafts;

    private OpenCount(Id recipe, ItemRef result, Id[] types, int[] amounts, int tick) {
      this.recipe = recipe;
      this.result = result;
      this.types = types;
      this.amounts = amounts;
      this.tick = tick;
    }

    private static OpenCount open(Id recipe, ItemRef result, List<ItemRef> matrix, int tick) {
      Id[] types = new Id[matrix.size()];
      int[] amounts = new int[matrix.size()];
      for (int slot = 0; slot < matrix.size(); slot++) {
        ItemRef stack = matrix.get(slot);
        if (!isEmpty(stack)) {
          types[slot] = stack.type();
          amounts[slot] = stack.amount();
        }
      }
      ItemRef snapshot = result == null ? null : result.copy();
      return new OpenCount(recipe, snapshot, types, amounts, tick);
    }

    private int craftsStarted(List<ItemRef> matrix) {
      if (matrix.size() != amounts.length) {
        return -1;
      }

      int[] remaining = new int[matrix.size()];
      for (int slot = 0; slot < matrix.size(); slot++) {
        ItemRef stack = matrix.get(slot);
        remaining[slot] = isEmpty(stack) || !stack.type().equals(types[slot]) ? 0 : stack.amount();
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
