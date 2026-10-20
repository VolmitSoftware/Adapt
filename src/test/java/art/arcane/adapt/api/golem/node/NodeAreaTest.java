package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeAreaTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000022f1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000022f2");

  @Test
  void theAreaIsTheBoxOfBlocksAroundTheBlockInFrontOfTheFace() {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).area(NodeCuboid.symmetric(2, 1, 3)).build();

    assertThat(NodeArea.covers(key, record, 0.5D, 65D, 0.5D)).isTrue();
    assertThat(NodeArea.covers(key, record, 2.9D, 66.5D, -2.5D)).isTrue();
    assertThat(NodeArea.covers(key, record, -1.5D, 64D, 3.99D)).isTrue();
    assertThat(NodeArea.covers(key, record, 3D, 65D, 0.5D)).isFalse();
    assertThat(NodeArea.covers(key, record, -2.01D, 65D, 0.5D)).isFalse();
    assertThat(NodeArea.covers(key, record, 0.5D, 63.99D, 0.5D)).isFalse();
    assertThat(NodeArea.covers(key, record, 0.5D, 67D, 0.5D)).isFalse();
    assertThat(NodeArea.covers(key, record, 0.5D, 65D, 4D)).isFalse();
  }

  @Test
  void aLopsidedAreaCoversOnlyTheBlocksBetweenItsFaces() {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.EAST);
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.EAST).area(new NodeCuboid(0, -1, -3, 6, 2, 0)).build();

    assertThat(NodeArea.center(key)).containsExactly(1, 64, 0);
    assertThat(NodeArea.covers(key, record, 1.5D, 64.5D, 0.5D)).isTrue();
    assertThat(NodeArea.covers(key, record, 7.99D, 66.5D, -2.5D)).isTrue();
    assertThat(NodeArea.covers(key, record, 1D, 63D, -3D)).isTrue();
    assertThat(NodeArea.covers(key, record, 0.99D, 64.5D, 0.5D)).isFalse();
    assertThat(NodeArea.covers(key, record, 8D, 64.5D, 0.5D)).isFalse();
    assertThat(NodeArea.covers(key, record, 1.5D, 62.99D, 0.5D)).isFalse();
    assertThat(NodeArea.covers(key, record, 1.5D, 67D, 0.5D)).isFalse();
    assertThat(NodeArea.covers(key, record, 1.5D, 64.5D, 1D)).isFalse();
    assertThat(NodeArea.covers(key, record, 1.5D, 64.5D, -3.01D)).isFalse();
  }
}
