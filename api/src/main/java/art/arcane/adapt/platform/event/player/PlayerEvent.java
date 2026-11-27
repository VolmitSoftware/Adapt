package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Base for player events with a plain cancelled flag. */
@ApiStatus.AvailableSince("1.0.0")
public abstract class PlayerEvent extends CancellableEvent {
  private final PlayerRef player;

  /** Creates the event for a player, who must not be null. */
  protected PlayerEvent(PlayerRef player) {
    this.player = Objects.requireNonNull(player, "player");
  }

  /** The player the event is about. */
  public PlayerRef player() {
    return player;
  }
}
