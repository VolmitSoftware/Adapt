package art.arcane.adapt.api.golem.plinth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PlinthIndex {
  private final Map<UUID, Map<Long, ChunkSlots>> worlds = new ConcurrentHashMap<>();
  private final AtomicLong generations = new AtomicLong();

  public long beginLoad(UUID world, long chunkKey) {
    long generation = generations.incrementAndGet();
    chunks(world).put(chunkKey, new ChunkSlots(generation));
    return generation;
  }

  public boolean completeLoad(UUID world, long chunkKey, long generation, Map<PlinthSpot, PlinthSlotItem> slots) {
    ChunkSlots chunk = chunk(world, chunkKey);
    if (chunk == null || chunk.generation != generation) {
      return false;
    }
    chunk.slots.putAll(slots);
    chunk.ready = true;
    return true;
  }

  public boolean isCurrent(UUID world, long chunkKey, long generation) {
    ChunkSlots chunk = chunk(world, chunkKey);
    return chunk != null && chunk.generation == generation;
  }

  public boolean isReady(UUID world, long chunkKey) {
    ChunkSlots chunk = chunk(world, chunkKey);
    return chunk != null && chunk.ready;
  }

  public List<PlinthSpot> unload(UUID world, long chunkKey) {
    Map<Long, ChunkSlots> chunks = worlds.get(world);
    ChunkSlots removed = chunks == null ? null : chunks.remove(chunkKey);
    return removed == null ? List.of() : List.copyOf(removed.slots.keySet());
  }

  public boolean unload(UUID world, long chunkKey, long generation) {
    Map<Long, ChunkSlots> chunks = worlds.get(world);
    ChunkSlots chunk = chunks == null ? null : chunks.get(chunkKey);
    return chunk != null && chunk.generation == generation && chunks.remove(chunkKey, chunk);
  }

  public List<PlinthSpot> unloadWorld(UUID world) {
    Map<Long, ChunkSlots> removed = worlds.remove(world);
    if (removed == null) {
      return List.of();
    }
    List<PlinthSpot> spots = new ArrayList<>();
    for (ChunkSlots chunk : removed.values()) {
      spots.addAll(chunk.slots.keySet());
    }
    return spots;
  }

  public boolean put(PlinthSpot spot, PlinthSlotItem item) {
    ChunkSlots chunk = chunk(spot.world(), spot.chunkKey());
    if (chunk == null || !chunk.ready) {
      return false;
    }
    chunk.slots.put(spot, item);
    return true;
  }

  public PlinthSlotItem remove(PlinthSpot spot) {
    ChunkSlots chunk = chunk(spot.world(), spot.chunkKey());
    return chunk == null ? null : chunk.slots.remove(spot);
  }

  public Optional<PlinthSlotItem> get(PlinthSpot spot) {
    ChunkSlots chunk = chunk(spot.world(), spot.chunkKey());
    return chunk == null ? Optional.empty() : Optional.ofNullable(chunk.slots.get(spot));
  }

  private Map<Long, ChunkSlots> chunks(UUID world) {
    return worlds.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>());
  }

  private ChunkSlots chunk(UUID world, long chunkKey) {
    Map<Long, ChunkSlots> chunks = worlds.get(world);
    return chunks == null ? null : chunks.get(chunkKey);
  }

  private static final class ChunkSlots {
    private final long generation;
    private final Map<PlinthSpot, PlinthSlotItem> slots = new ConcurrentHashMap<>();
    private volatile boolean ready;

    private ChunkSlots(long generation) {
      this.generation = generation;
    }
  }
}
