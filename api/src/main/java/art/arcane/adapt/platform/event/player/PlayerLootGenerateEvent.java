package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player opening a naturally generated loot container (chest, barrel, minecart chest) rolled its loot table.
 * Rolls started by plugins are not posted. Read-only.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerLootGenerateEvent extends AdaptEvent {
  private final PlayerRef player;
  private final Loc location;

  public PlayerLootGenerateEvent(PlayerRef player, Loc location) {
    this.player = Objects.requireNonNull(player, "player");
    this.location = Objects.requireNonNull(location, "location");
  }

  /** The player who opened the container. */
  public PlayerRef player() {
    return player;
  }

  /** Origin of the loot roll, at the container. */
  public Loc location() {
    return location;
  }
}
