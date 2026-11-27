package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * Player toggles sprint. {@link #isSprinting()} is the new state. Cancelling keeps the current state. Mutable:
 * cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerToggleSprintEvent extends PlayerEvent {
  private final boolean sprinting;

  public PlayerToggleSprintEvent(PlayerRef player, boolean sprinting) {
    super(player);
    this.sprinting = sprinting;
  }

  /** True when the player starts sprinting, false when the player stops. */
  public boolean isSprinting() {
    return sprinting;
  }
}
