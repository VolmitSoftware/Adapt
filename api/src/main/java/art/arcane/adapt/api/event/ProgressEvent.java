package art.arcane.adapt.api.event;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A change to one player's progress that is about to happen. Cancelling it stops the change; the caller of the
 * profile method gets a {@code CANCELLED} result. Fired on the thread that owns the player.
 */
@ApiStatus.AvailableSince("1.0.0")
public abstract class ProgressEvent extends CancellableEvent {
  private final PlayerRef player;

  /** An event about {@code player}'s progress. */
  protected ProgressEvent(PlayerRef player) {
    this.player = Objects.requireNonNull(player, "player");
  }

  /** The player whose progress changes. */
  public final PlayerRef player() {
    return player;
  }
}
