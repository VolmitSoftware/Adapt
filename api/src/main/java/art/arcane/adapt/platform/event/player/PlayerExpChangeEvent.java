package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Experience points about to be given to a player (orbs, smelting, trading). Not cancellable; set the amount to 0
 * to give nothing. Mutable: amount.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerExpChangeEvent extends AdaptEvent {
  private final PlayerRef player;
  private int amount;

  public PlayerExpChangeEvent(PlayerRef player, int amount) {
    this.player = Objects.requireNonNull(player, "player");
    this.amount = amount;
  }

  /** The player receiving the experience. */
  public PlayerRef player() {
    return player;
  }

  /** Experience points about to be given. */
  public int amount() {
    return amount;
  }

  /** Changes the points given. */
  public void setAmount(int amount) {
    this.amount = amount;
  }
}
