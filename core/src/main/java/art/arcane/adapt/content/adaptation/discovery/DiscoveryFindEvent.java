package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/**
 * The Discovery skill recorded something the player had never found before: a block, item, creature, biome, world or
 * the like, or with {@code chunk} a chunk they had never entered. Read-only.
 */
public class DiscoveryFindEvent extends AdaptEvent {
  private final PlayerRef player;
  private final Loc at;
  private final boolean chunk;

  public DiscoveryFindEvent(PlayerRef player, Loc at, boolean chunk) {
    this.player = Objects.requireNonNull(player, "player");
    this.at = Objects.requireNonNull(at, "at");
    this.chunk = chunk;
  }

  public PlayerRef player() {
    return player;
  }

  public Loc at() {
    return at;
  }

  public boolean chunk() {
    return chunk;
  }
}
