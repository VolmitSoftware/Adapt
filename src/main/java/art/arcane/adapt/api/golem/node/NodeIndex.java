package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemRoster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class NodeIndex {
  private final Map<UUID, Map<Long, ChunkNodes>> worlds = new ConcurrentHashMap<>();
  private final AtomicLong generations = new AtomicLong();

  public long beginLoad(UUID world, long chunkKey) {
    long generation = generations.incrementAndGet();
    chunks(world).put(chunkKey, new ChunkNodes(generation));
    return generation;
  }

  public boolean completeLoad(UUID world, long chunkKey, long generation, Map<NodeKey, NodeRecord> nodes) {
    ChunkNodes chunk = chunk(world, chunkKey);
    if (chunk == null || chunk.generation != generation) {
      return false;
    }
    chunk.nodes.putAll(nodes);
    chunk.ready = true;
    return true;
  }

  public boolean isCurrent(UUID world, long chunkKey, long generation) {
    ChunkNodes chunk = chunk(world, chunkKey);
    return chunk != null && chunk.generation == generation;
  }

  public boolean isReady(UUID world, long chunkKey) {
    ChunkNodes chunk = chunk(world, chunkKey);
    return chunk != null && chunk.ready;
  }

  public List<NodeKey> unload(UUID world, long chunkKey) {
    Map<Long, ChunkNodes> chunks = worlds.get(world);
    ChunkNodes removed = chunks == null ? null : chunks.remove(chunkKey);
    return removed == null ? List.of() : List.copyOf(removed.nodes.keySet());
  }

  public boolean unload(UUID world, long chunkKey, long generation) {
    Map<Long, ChunkNodes> chunks = worlds.get(world);
    ChunkNodes chunk = chunks == null ? null : chunks.get(chunkKey);
    return chunk != null && chunk.generation == generation && chunks.remove(chunkKey, chunk);
  }

  public List<NodeKey> unloadWorld(UUID world) {
    Map<Long, ChunkNodes> removed = worlds.remove(world);
    if (removed == null) {
      return List.of();
    }
    List<NodeKey> keys = new ArrayList<>();
    for (ChunkNodes chunk : removed.values()) {
      keys.addAll(chunk.nodes.keySet());
    }
    return keys;
  }

  public boolean put(NodeKey key, NodeRecord record) {
    ChunkNodes chunk = chunk(key.world(), key.chunkKey());
    if (chunk == null || !chunk.ready) {
      return false;
    }
    chunk.nodes.put(key, record);
    return true;
  }

  public NodeRecord remove(NodeKey key) {
    ChunkNodes chunk = chunk(key.world(), key.chunkKey());
    return chunk == null ? null : chunk.nodes.remove(key);
  }

  public Optional<NodeRecord> get(NodeKey key) {
    ChunkNodes chunk = chunk(key.world(), key.chunkKey());
    return chunk == null ? Optional.empty() : Optional.ofNullable(chunk.nodes.get(key));
  }

  public int count(UUID world, long chunkKey) {
    ChunkNodes chunk = chunk(world, chunkKey);
    return chunk == null ? 0 : chunk.nodes.size();
  }

  public List<Entry> near(UUID world, double x, double y, double z, double range) {
    Map<Long, ChunkNodes> chunks = worlds.get(world);
    if (chunks == null || chunks.isEmpty()) {
      return List.of();
    }
    int minChunkX = (int) Math.floor((x - range) / 16D);
    int maxChunkX = (int) Math.floor((x + range) / 16D);
    int minChunkZ = (int) Math.floor((z - range) / 16D);
    int maxChunkZ = (int) Math.floor((z + range) / 16D);
    double rangeSquared = range * range;
    List<Entry> found = new ArrayList<>();
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        ChunkNodes chunk = chunks.get(GolemRoster.chunkKey(chunkX, chunkZ));
        if (chunk == null || !chunk.ready) {
          continue;
        }
        for (Map.Entry<NodeKey, NodeRecord> node : chunk.nodes.entrySet()) {
          if (node.getKey().distanceSquared(x, y, z) <= rangeSquared) {
            found.add(new Entry(node.getKey(), node.getValue()));
          }
        }
      }
    }
    return found;
  }

  private Map<Long, ChunkNodes> chunks(UUID world) {
    return worlds.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>());
  }

  private ChunkNodes chunk(UUID world, long chunkKey) {
    Map<Long, ChunkNodes> chunks = worlds.get(world);
    return chunks == null ? null : chunks.get(chunkKey);
  }

  public record Entry(NodeKey key, NodeRecord record) {
  }

  private static final class ChunkNodes {
    private final long generation;
    private final Map<NodeKey, NodeRecord> nodes = new ConcurrentHashMap<>();
    private volatile boolean ready;

    private ChunkNodes(long generation) {
      this.generation = generation;
    }
  }
}
