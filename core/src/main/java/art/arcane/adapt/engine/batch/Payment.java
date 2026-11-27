package art.arcane.adapt.engine.batch;

import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.Nullable;

/** Where a build batch takes the item for each block it places. */
public interface Payment {
  /** Takes one item similar to {@code unit} and returns exactly what was taken (amount 1), or null when none is left. */
  @Nullable ItemRef take(ItemRef unit);

  /** Puts back an item {@link #take} returned, when its block could not be placed after all. */
  void giveBack(ItemRef taken);
}
