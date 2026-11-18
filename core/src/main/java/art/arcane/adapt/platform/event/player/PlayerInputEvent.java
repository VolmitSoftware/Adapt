package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/** The movement keys a player holds changed. Read-only. */
public class PlayerInputEvent extends AdaptEvent {
  private final PlayerRef player;
  private final PlayerInput input;

  public PlayerInputEvent(PlayerRef player, PlayerInput input) {
    this.player = Objects.requireNonNull(player, "player");
    this.input = Objects.requireNonNull(input, "input");
  }

  public PlayerRef player() {
    return player;
  }

  public PlayerInput input() {
    return input;
  }
}
