package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * Enough players slept and the world skips the night. sleepers are the players asleep long enough at that moment.
 * Not cancellable.
 */
@ApiStatus.AvailableSince("1.0.0")
public class NightSkipEvent extends AdaptEvent {
  private final WorldRef world;
  private final List<PlayerRef> sleepers;

  public NightSkipEvent(WorldRef world, List<PlayerRef> sleepers) {
    this.world = Objects.requireNonNull(world, "world");
    this.sleepers = List.copyOf(sleepers);
  }

  /** The world whose night is skipped. */
  public WorldRef world() {
    return world;
  }

  /** Read-only list of the players asleep long enough. */
  public List<PlayerRef> sleepers() {
    return sleepers;
  }
}
