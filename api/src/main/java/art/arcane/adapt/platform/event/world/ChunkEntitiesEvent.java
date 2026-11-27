package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * The entities of a chunk finished loading or are about to unload (entity storage is separate from
 * block data). Read-only.
 */
@ApiStatus.AvailableSince("1.0.0")
public class ChunkEntitiesEvent extends AdaptEvent {
  private final WorldRef world;
  private final ChunkPos chunk;
  private final Phase phase;
  private final List<EntityRef> entities;

  public ChunkEntitiesEvent(WorldRef world, ChunkPos chunk, Phase phase, List<EntityRef> entities) {
    this.world = Objects.requireNonNull(world, "world");
    this.chunk = Objects.requireNonNull(chunk, "chunk");
    this.phase = phase == null ? Phase.LOAD : phase;
    this.entities = List.copyOf(entities);
  }

  /** The chunk's world. */
  public WorldRef world() {
    return world;
  }

  /** Chunk coordinates. */
  public ChunkPos chunk() {
    return chunk;
  }

  /** Whether the entities loaded or are unloading. */
  public Phase phase() {
    return phase;
  }

  /** Read-only list of the chunk's entities. */
  public List<EntityRef> entities() {
    return entities;
  }

  /**
   * Whether a chunk's entities loaded or are about to unload.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Phase {
    LOAD,
    UNLOAD
  }
}
