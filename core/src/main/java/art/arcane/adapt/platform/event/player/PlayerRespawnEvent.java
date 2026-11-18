package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/** A player respawns. Mutable: respawn location. */
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

  public PlayerRef player() {
    return player;
  }

  public Loc respawnLocation() {
    return respawnLocation;
  }

  public void setRespawnLocation(Loc location) {
    this.respawnLocation = Objects.requireNonNull(location, "location");
  }

  public boolean isBedSpawn() {
    return bedSpawn;
  }

  public boolean isAnchorSpawn() {
    return anchorSpawn;
  }
}
