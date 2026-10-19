package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeIndexTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  @Test
  void nodeKeysPackTheirChunkAndAnchor() {
    NodeKey key = new NodeKey(WORLD, -17, 70, 33, BlockFace.UP);

    assertThat(key.chunkX()).isEqualTo(-2);
    assertThat(key.chunkZ()).isEqualTo(2);
    assertThat(key.chunkKey()).isEqualTo(GolemRoster.chunkKey(-2, 2));
    assertThat(key.anchor().world()).isEqualTo(WORLD);
    assertThat(List.of(key.anchor().x(), key.anchor().y(), key.anchor().z())).containsExactly(-17, 70, 33);
    assertThat(key.distanceSquared(-16.5D, 70.5D, 33.5D)).isEqualTo(0D);
  }

  @Test
  void aChunkIsNotReadyUntilItsMantleReadCompletes() {
    NodeIndex index = new NodeIndex();
    NodeKey key = new NodeKey(WORLD, 1, 64, 1, BlockFace.NORTH);

    long generation = index.beginLoad(WORLD, key.chunkKey());

    assertThat(index.isReady(WORLD, key.chunkKey())).isFalse();
    assertThat(index.put(key, record(BlockFace.NORTH))).isFalse();
    assertThat(index.completeLoad(WORLD, key.chunkKey(), generation, Map.of(key, record(BlockFace.NORTH)))).isTrue();
    assertThat(index.isReady(WORLD, key.chunkKey())).isTrue();
    assertThat(index.get(key)).contains(record(BlockFace.NORTH));
    assertThat(index.count(WORLD, key.chunkKey())).isEqualTo(1);
  }

  @Test
  void aStaleLoadCannotOverwriteANewerOneOrAnUnloadedChunk() {
    NodeIndex index = new NodeIndex();
    NodeKey key = new NodeKey(WORLD, 1, 64, 1, BlockFace.NORTH);
    long first = index.beginLoad(WORLD, key.chunkKey());
    long second = index.beginLoad(WORLD, key.chunkKey());

    assertThat(index.completeLoad(WORLD, key.chunkKey(), first, Map.of(key, record(BlockFace.NORTH)))).isFalse();
    assertThat(index.isCurrent(WORLD, key.chunkKey(), first)).isFalse();
    assertThat(index.isCurrent(WORLD, key.chunkKey(), second)).isTrue();

    index.unload(WORLD, key.chunkKey());

    assertThat(index.completeLoad(WORLD, key.chunkKey(), second, Map.of(key, record(BlockFace.NORTH)))).isFalse();
    assertThat(index.get(key)).isEmpty();
  }

  @Test
  void unloadingAChunkReturnsAndForgetsItsNodes() {
    NodeIndex index = new NodeIndex();
    NodeKey north = new NodeKey(WORLD, 1, 64, 1, BlockFace.NORTH);
    NodeKey up = new NodeKey(WORLD, 2, 64, 1, BlockFace.UP);
    ready(index, Map.of(north, record(BlockFace.NORTH), up, record(BlockFace.UP)));

    assertThat(index.get(north)).isPresent();
    assertThat(index.unload(WORLD, north.chunkKey())).containsExactlyInAnyOrder(north, up);
    assertThat(index.get(north)).isEmpty();
    assertThat(index.isReady(WORLD, north.chunkKey())).isFalse();
    assertThat(index.count(WORLD, north.chunkKey())).isZero();
  }

  @Test
  void placedAndRemovedNodesChangeTheChunkCount() {
    NodeIndex index = new NodeIndex();
    NodeKey existing = new NodeKey(WORLD, 3, 64, 3, BlockFace.EAST);
    NodeKey placed = new NodeKey(WORLD, 4, 64, 3, BlockFace.WEST);
    ready(index, Map.of(existing, record(BlockFace.EAST)));

    assertThat(index.put(placed, record(BlockFace.WEST))).isTrue();
    assertThat(index.count(WORLD, placed.chunkKey())).isEqualTo(2);
    assertThat(index.get(placed)).contains(record(BlockFace.WEST));
    assertThat(index.remove(existing)).isEqualTo(record(BlockFace.EAST));
    assertThat(index.get(existing)).isEmpty();
    assertThat(index.count(WORLD, placed.chunkKey())).isEqualTo(1);
    assertThat(index.remove(existing)).isNull();
  }

  @Test
  void nearbyQueriesSpanChunksAndRespectRangeAndWorld() {
    NodeIndex index = new NodeIndex();
    NodeKey close = new NodeKey(WORLD, 15, 64, 0, BlockFace.UP);
    NodeKey acrossChunk = new NodeKey(WORLD, 20, 64, 0, BlockFace.UP);
    NodeKey far = new NodeKey(WORLD, 60, 64, 0, BlockFace.UP);
    NodeKey high = new NodeKey(WORLD, 16, 100, 0, BlockFace.UP);
    NodeKey elsewhere = new NodeKey(OTHER_WORLD, 15, 64, 0, BlockFace.UP);
    ready(index, Map.of(close, record(BlockFace.UP)));
    ready(index, Map.of(acrossChunk, record(BlockFace.UP), high, record(BlockFace.UP)));
    ready(index, Map.of(far, record(BlockFace.UP)));
    ready(index, Map.of(elsewhere, record(BlockFace.UP)));

    List<NodeIndex.Entry> near = index.near(WORLD, 14.5D, 64.5D, 0.5D, 8D);

    assertThat(near).extracting(NodeIndex.Entry::key).containsExactlyInAnyOrder(close, acrossChunk);
  }

  @Test
  void unloadingAWorldDropsEveryChunkInIt() {
    NodeIndex index = new NodeIndex();
    NodeKey a = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeKey b = new NodeKey(WORLD, 100, 64, 100, BlockFace.UP);
    NodeKey c = new NodeKey(OTHER_WORLD, 0, 64, 0, BlockFace.UP);
    ready(index, Map.of(a, record(BlockFace.UP)));
    ready(index, Map.of(b, record(BlockFace.UP)));
    ready(index, Map.of(c, record(BlockFace.UP)));

    assertThat(index.unloadWorld(WORLD)).containsExactlyInAnyOrder(a, b);
    assertThat(index.get(a)).isEmpty();
    assertThat(index.get(b)).isEmpty();
    assertThat(index.get(c)).isPresent();
  }

  @Test
  void aBlockMayAnchorANodeOnAnyFaceOrWhileItsChunksNodesAreUnknown() {
    NodeIndex index = new NodeIndex();
    NodeKey anchored = new NodeKey(WORLD, -17, 70, 33, BlockFace.WEST);
    ready(index, Map.of(anchored, record(BlockFace.WEST)));
    long loading = new NodeKey(WORLD, 40, 64, 40, BlockFace.UP).chunkKey();
    index.beginLoad(WORLD, loading);

    assertThat(index.mayAnchor(WORLD, -17, 70, 33)).isTrue();
    assertThat(index.mayAnchor(WORLD, -17, 71, 33)).isFalse();
    assertThat(index.mayAnchor(WORLD, -18, 70, 33)).isFalse();
    assertThat(index.mayAnchor(OTHER_WORLD, -17, 70, 33)).isTrue();
    assertThat(index.mayAnchor(WORLD, 40, 64, 40)).isTrue();
    assertThat(index.mayAnchor(WORLD, 200, 64, 200)).isTrue();
  }

  private static void ready(NodeIndex index, Map<NodeKey, NodeRecord> nodes) {
    NodeKey any = nodes.keySet().iterator().next();
    index.completeLoad(any.world(), any.chunkKey(), index.beginLoad(any.world(), any.chunkKey()), nodes);
  }

  private static NodeRecord record(BlockFace face) {
    return NodeRecord.builder(OWNER, NodeType.GUARD, face).build();
  }
}
