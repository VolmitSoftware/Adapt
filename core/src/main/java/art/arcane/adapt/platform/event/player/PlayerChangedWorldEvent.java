package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Objects;

/** A player arrived in another world. The player handle stays valid. */
public class PlayerChangedWorldEvent extends AdaptEvent {
  private final PlayerRef player;
  private final WorldRef from;

  public PlayerChangedWorldEvent(PlayerRef player, WorldRef from) {
    this.player = Objects.requireNonNull(player, "player");
    this.from = Objects.requireNonNull(from, "from");
  }

  public PlayerRef player() {
    return player;
  }

  public WorldRef from() {
    return from;
  }
}
