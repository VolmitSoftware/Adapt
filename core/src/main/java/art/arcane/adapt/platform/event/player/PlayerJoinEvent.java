package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;

/** A player joined. Posted after the player is in the world. */
public class PlayerJoinEvent extends AdaptEvent {
  private final PlayerRef player;

  public PlayerJoinEvent(PlayerRef player) {
    this.player = Objects.requireNonNull(player, "player");
  }

  public PlayerRef player() {
    return player;
  }
}
