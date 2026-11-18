package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Objects;

/**
 * A chunk was loaded or is about to unload. newChunk() is true when the chunk was just generated.
 * Not cancellable.
 */
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

  public WorldRef world() {
    return world;
  }

  public ChunkPos chunk() {
    return chunk;
  }

  public Phase phase() {
    return phase;
  }

  public boolean newChunk() {
    return newChunk;
  }

  public enum Phase {
    LOAD,
    UNLOAD
  }
}
