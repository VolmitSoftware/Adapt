package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

/** Food level about to change. Mutable: new level, cancelled. */
public class FoodLevelChangeEvent extends PlayerEvent {
  private final ItemRef item;
  private int foodLevel;

  public FoodLevelChangeEvent(PlayerRef player, int foodLevel, ItemRef item) {
    super(player);
    this.foodLevel = foodLevel;
    this.item = item;
  }

  public int foodLevel() {
    return foodLevel;
  }

  public void setFoodLevel(int foodLevel) {
    this.foodLevel = Math.max(0, Math.min(20, foodLevel));
  }

  /** Food being eaten, or null for hunger loss. */
  public ItemRef item() {
    return item;
  }
}
