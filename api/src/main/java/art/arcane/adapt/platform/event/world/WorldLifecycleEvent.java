package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A world loaded, is being saved or is about to unload. Mutable: cancelled, honoured only for
 * UNLOAD on Bukkit, where it keeps the world loaded. Modded platforms ignore it, since their worlds unload only when
 * the server stops.
 */
@ApiStatus.AvailableSince("1.0.0")
public class WorldLifecycleEvent extends CancellableEvent {
  private final WorldRef world;
  private final Phase phase;

  public WorldLifecycleEvent(WorldRef world, Phase phase) {
    this.world = Objects.requireNonNull(world, "world");
    this.phase = phase == null ? Phase.LOAD : phase;
  }

  /** The world. */
  public WorldRef world() {
    return world;
  }

  /** Whether the world loaded, is saving or is unloading. */
  public Phase phase() {
    return phase;
  }

  /**
   * The lifecycle moment a world event reports.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Phase {
    LOAD,
    SAVE,
    UNLOAD
  }
}
