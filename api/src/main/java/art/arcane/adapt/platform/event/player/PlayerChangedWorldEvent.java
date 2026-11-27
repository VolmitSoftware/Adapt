package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player arrived in another world. The player handle stays valid. Read-only. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerChangedWorldEvent extends AdaptEvent {
  private final PlayerRef player;
  private final WorldRef from;

  public PlayerChangedWorldEvent(PlayerRef player, WorldRef from) {
    this.player = Objects.requireNonNull(player, "player");
    this.from = Objects.requireNonNull(from, "from");
  }

  /** The player, already in the new world. */
  public PlayerRef player() {
    return player;
  }

  /** The world the player left. */
  public WorldRef from() {
    return from;
  }
}
