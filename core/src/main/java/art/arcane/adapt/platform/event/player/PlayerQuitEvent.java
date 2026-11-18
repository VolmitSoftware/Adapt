package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/** A player is leaving. The handle is still usable during dispatch, then goes stale. */
public class PlayerQuitEvent extends AdaptEvent {
  private final PlayerRef player;

  public PlayerQuitEvent(PlayerRef player) {
    this.player = Objects.requireNonNull(player, "player");
  }

  public PlayerRef player() {
    return player;
  }
}
