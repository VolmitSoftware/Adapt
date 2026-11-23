package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/** A player opening a naturally generated loot container (chest, barrel, minecart chest) rolled its loot table. */
public class PlayerLootGenerateEvent extends AdaptEvent {
  private final PlayerRef player;
  private final Loc location;

  public PlayerLootGenerateEvent(PlayerRef player, Loc location) {
    this.player = Objects.requireNonNull(player, "player");
    this.location = Objects.requireNonNull(location, "location");
  }

  public PlayerRef player() {
    return player;
  }

  public Loc location() {
    return location;
  }
}
