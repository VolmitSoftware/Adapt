package art.arcane.adapt.platform.event.world;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Objects;

/**
 * The entities of a chunk finished loading or are about to unload (entity storage is separate from
 * block data). Read-only.
 */
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

  public WorldRef world() {
    return world;
  }

  public ChunkPos chunk() {
    return chunk;
  }

  public Phase phase() {
    return phase;
  }

  public List<EntityRef> entities() {
    return entities;
  }

  public enum Phase {
    LOAD,
    UNLOAD
  }
}
