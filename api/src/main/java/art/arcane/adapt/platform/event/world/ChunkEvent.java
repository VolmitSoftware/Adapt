package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A chunk was loaded or is about to unload. newChunk() is true when the chunk was just generated.
 * Not cancellable.
 */
@ApiStatus.AvailableSince("1.0.0")
public class ChunkEvent extends AdaptEvent {
  private final WorldRef world;
  private final ChunkPos chunk;
  private final Phase phase;
  private final boolean newChunk;

  public ChunkEvent(WorldRef world, ChunkPos chunk, Phase phase, boolean newChunk) {
    this.world = Objects.requireNonNull(world, "world");
    this.chunk = Objects.requireNonNull(chunk, "chunk");
    this.phase = phase == null ? Phase.LOAD : phase;
    this.newChunk = newChunk;
  }

  /** The chunk's world. */
  public WorldRef world() {
    return world;
  }

  /** Chunk coordinates. */
  public ChunkPos chunk() {
    return chunk;
  }

  /** Whether the chunk loaded or is unloading. */
  public Phase phase() {
    return phase;
  }

  /** True when this load generated the chunk. Always false for an unload. */
  public boolean newChunk() {
    return newChunk;
  }

  /**
   * Whether a chunk loaded or is about to unload.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Phase {
    LOAD,
    UNLOAD
  }
}
