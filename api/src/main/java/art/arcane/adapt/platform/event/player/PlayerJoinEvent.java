package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player joined. Posted after the player is in the world. Read-only. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerJoinEvent extends AdaptEvent {
  private final PlayerRef player;

  public PlayerJoinEvent(PlayerRef player) {
    this.player = Objects.requireNonNull(player, "player");
  }

  /** The player who joined. */
  public PlayerRef player() {
    return player;
  }
}
