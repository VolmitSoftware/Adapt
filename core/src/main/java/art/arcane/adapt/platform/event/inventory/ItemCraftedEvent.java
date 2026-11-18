package art.arcane.adapt.platform.event.inventory;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** One craft completed and the result reached the player. Posted once per crafted batch. */
public class ItemCraftedEvent extends AdaptEvent {
  private final PlayerRef player;
  private final ItemRef crafted;
  private final Id recipeKey;

  public ItemCraftedEvent(PlayerRef player, ItemRef crafted, Id recipeKey) {
    this.player = Objects.requireNonNull(player, "player");
    this.crafted = Objects.requireNonNull(crafted, "crafted");
    this.recipeKey = recipeKey;
  }

  public PlayerRef player() {
    return player;
  }

  public ItemRef crafted() {
    return crafted;
  }

  public Id recipeKey() {
    return recipeKey;
  }
}
