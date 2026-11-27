package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * Player toggles sneak. {@link #isSneaking()} is the new state. Cancelling keeps the current state. Mutable:
 * cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerToggleSneakEvent extends PlayerEvent {
  private final boolean sneaking;

  public PlayerToggleSneakEvent(PlayerRef player, boolean sneaking) {
    super(player);
    this.sneaking = sneaking;
  }

  /** True when the player starts sneaking, false when the player stops. */
  public boolean isSneaking() {
    return sneaking;
  }
}
