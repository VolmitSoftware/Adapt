package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * The movement keys a player holds changed: posted when a client input packet differs from the previous one.
 * Read-only.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerInputEvent extends AdaptEvent {
  private final PlayerRef player;
  private final PlayerInput input;

  public PlayerInputEvent(PlayerRef player, PlayerInput input) {
    this.player = Objects.requireNonNull(player, "player");
    this.input = Objects.requireNonNull(input, "input");
  }

  /** The player whose keys changed. */
  public PlayerRef player() {
    return player;
  }

  /** The keys held after the change. */
  public PlayerInput input() {
    return input;
  }
}
