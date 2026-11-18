package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** Base for cancellable player events. */
public abstract class PlayerEvent extends CancellableEvent {
  private final PlayerRef player;

  protected PlayerEvent(PlayerRef player) {
    this.player = Objects.requireNonNull(player, "player");
  }

  public PlayerRef player() {
    return player;
  }
}
