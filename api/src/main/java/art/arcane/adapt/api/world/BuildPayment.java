package art.arcane.adapt.api.world;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Where a {@link BuildBatch} takes the item for each block it places. */
@ApiStatus.AvailableSince("1.0.0")
public interface BuildPayment {
  /** Takes one item similar to {@code unit} and returns exactly what was taken (amount 1), or null when none is left. */
  @Nullable ItemRef take(ItemRef unit);

  /** Puts back an item {@link #take} returned, when its block could not be placed after all. */
  void giveBack(ItemRef taken);

  /** The player's main hand first, then the off hand, then the rest of the inventory. Needs a running core. */
  static BuildPayment player(PlayerRef player) {
    return Engine.bridge().builds().playerPayment(player);
  }

  /** A container; what could not be placed goes back into it, or to {@code owner} when it is full. */
  static BuildPayment container(InventoryRef container, PlayerRef owner) {
    return Engine.bridge().builds().containerPayment(container, owner);
  }

  /** Puts {@code items} in the player's inventory and drops what does not fit at their feet; skips empty items. */
  static void give(PlayerRef player, List<ItemRef> items) {
    Engine.bridge().builds().give(player, items);
  }
}
