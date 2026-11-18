package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Objects;

/**
 * A world loaded, is being saved or is about to unload. Mutable: cancelled, honoured only for
 * UNLOAD.
 */
public class WorldLifecycleEvent extends CancellableEvent {
  private final WorldRef world;
  private final Phase phase;

  public WorldLifecycleEvent(WorldRef world, Phase phase) {
    this.world = Objects.requireNonNull(world, "world");
    this.phase = phase == null ? Phase.LOAD : phase;
  }

  public WorldRef world() {
    return world;
  }

  public Phase phase() {
    return phase;
  }

  public enum Phase {
    LOAD,
    SAVE,
    UNLOAD
  }
}
