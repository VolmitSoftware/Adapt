package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/** Experience points about to be added (orbs, smelting, trading). Mutable: amount. */
public class PlayerExpChangeEvent extends AdaptEvent {
  private final PlayerRef player;
  private int amount;

  public PlayerExpChangeEvent(PlayerRef player, int amount) {
    this.player = Objects.requireNonNull(player, "player");
    this.amount = amount;
  }

  public PlayerRef player() {
    return player;
  }

  public int amount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }
}
