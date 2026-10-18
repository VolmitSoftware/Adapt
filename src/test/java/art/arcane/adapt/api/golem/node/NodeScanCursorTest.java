package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeScanCursorTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  @Test
  void theWorkAreaIsCenteredOnTheBlockInFrontOfTheFace() {
    assertThat(NodeArea.center(new NodeKey(WORLD, 10, 64, -5, BlockFace.UP))).containsExactly(10, 65, -5);
    assertThat(NodeArea.center(new NodeKey(WORLD, 10, 64, -5, BlockFace.DOWN))).containsExactly(10, 63, -5);
    assertThat(NodeArea.center(new NodeKey(WORLD, 10, 64, -5, BlockFace.NORTH))).containsExactly(10, 64, -6);
    assertThat(NodeArea.center(new NodeKey(WORLD, 10, 64, -5, BlockFace.SOUTH))).containsExactly(10, 64, -4);
    assertThat(NodeArea.center(new NodeKey(WORLD, 10, 64, -5, BlockFace.EAST))).containsExactly(11, 64, -5);
    assertThat(NodeArea.center(new NodeKey(WORLD, 10, 64, -5, BlockFace.WEST))).containsExactly(9, 64, -5);
  }

  @Test
  void theAreaVolumeCoversEveryBlockWithinTheRadii() {
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(2, 1, 3).build();

    assertThat(NodeArea.volume(node)).isEqualTo(5 * 3 * 7);
  }

  @Test
  void everyIndexMapsToADistinctBlockInsideTheArea() {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(1, 0, 2).build();
    Set<String> seen = new HashSet<>();

    for (int index = 0; index < NodeArea.volume(node); index++) {
      int[] position = NodeArea.position(key, node, index);
      assertThat(position[0]).isBetween(-1, 1);
      assertThat(position[1]).isEqualTo(65);
      assertThat(position[2]).isBetween(-2, 2);
      seen.add(position[0] + "," + position[1] + "," + position[2]);
    }

    assertThat(seen).hasSize(15);
  }

  @Test
  void theCursorAdvancesByTheBudgetAndWrapsAroundTheArea() {
    NodeScanCursor cursor = new NodeScanCursor();

    assertThat(cursor.next(64, 100)).isEqualTo(new NodeScanCursor.Window(0, 64));
    assertThat(cursor.next(64, 100)).isEqualTo(new NodeScanCursor.Window(64, 36));
    assertThat(cursor.next(64, 100)).isEqualTo(new NodeScanCursor.Window(0, 64));
  }

  @Test
  void aSmallAreaIsScannedWholeEachPass() {
    NodeScanCursor cursor = new NodeScanCursor();

    assertThat(cursor.next(64, 27)).isEqualTo(new NodeScanCursor.Window(0, 27));
    assertThat(cursor.next(64, 27)).isEqualTo(new NodeScanCursor.Window(0, 27));
  }

  @Test
  void aShrunkAreaRestartsTheCursorInsideIt() {
    NodeScanCursor cursor = new NodeScanCursor();
    cursor.next(64, 1000);
    cursor.next(64, 1000);

    assertThat(cursor.next(64, 100)).isEqualTo(new NodeScanCursor.Window(0, 64));
    assertThat(cursor.next(0, 100)).isEqualTo(new NodeScanCursor.Window(64, 0));
  }
}
