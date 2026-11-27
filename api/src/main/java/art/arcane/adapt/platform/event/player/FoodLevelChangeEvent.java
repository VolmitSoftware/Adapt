package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * A player's food level is about to change, from eating or from hunger loss. Cancelling keeps the current level.
 * Mutable: new level, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class FoodLevelChangeEvent extends PlayerEvent {
  private final ItemRef item;
  private int foodLevel;

  public FoodLevelChangeEvent(PlayerRef player, int foodLevel, ItemRef item) {
    super(player);
    this.foodLevel = foodLevel;
    this.item = item;
  }

  /** The food level the player will have after the change. */
  public int foodLevel() {
    return foodLevel;
  }

  /** Changes the resulting food level, clamped to 0 to 20. */
  public void setFoodLevel(int foodLevel) {
    this.foodLevel = Math.max(0, Math.min(20, foodLevel));
  }

  /** Food being eaten, or null for hunger loss. */
  public ItemRef item() {
    return item;
  }
}
