package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * Player toggles flight. {@link #isFlying()} is the new state. Cancelling keeps the current state. Mutable:
 * cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerToggleFlightEvent extends PlayerEvent {
  private final boolean flying;

  public PlayerToggleFlightEvent(PlayerRef player, boolean flying) {
    super(player);
    this.flying = flying;
  }

  /** True when the player starts flying, false when the player stops. */
  public boolean isFlying() {
    return flying;
  }
}
