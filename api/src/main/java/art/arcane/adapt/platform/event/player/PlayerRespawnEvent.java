package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player respawns after death. Not cancellable. Mutable: respawn location. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerRespawnEvent extends AdaptEvent {
  private final PlayerRef player;
  private final boolean bedSpawn;
  private final boolean anchorSpawn;
  private Loc respawnLocation;

  public PlayerRespawnEvent(PlayerRef player, Loc respawnLocation, boolean bedSpawn, boolean anchorSpawn) {
    this.player = Objects.requireNonNull(player, "player");
    this.respawnLocation = Objects.requireNonNull(respawnLocation, "respawnLocation");
    this.bedSpawn = bedSpawn;
    this.anchorSpawn = anchorSpawn;
  }

  /** The respawning player. */
  public PlayerRef player() {
    return player;
  }

  /** Where the player will appear. */
  public Loc respawnLocation() {
    return respawnLocation;
  }

  /** Moves the respawn to this location. */
  public void setRespawnLocation(Loc location) {
    this.respawnLocation = Objects.requireNonNull(location, "location");
  }

  /** True when the location comes from the player's bed. Always false on Folia, which cannot tell spawn kinds apart. */
  public boolean isBedSpawn() {
    return bedSpawn;
  }

  /** True when the location comes from a respawn anchor. Always false on Folia, which cannot tell spawn kinds apart. */
  public boolean isAnchorSpawn() {
    return anchorSpawn;
  }
}
