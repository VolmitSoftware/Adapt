package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
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
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(2, 1, 3)).build();

    assertThat(NodeArea.volume(node)).isEqualTo(5 * 3 * 7);
  }

  @Test
  void everyIndexMapsToADistinctBlockInsideTheArea() {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(1, 0, 2)).build();
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
  void everyIndexOfALopsidedAreaMapsToADistinctBlockBetweenItsFaces() {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(new NodeCuboid(-1, 0, -4, 5, 2, 0)).build();
    Set<String> seen = new HashSet<>();

    for (int index = 0; index < NodeArea.volume(node); index++) {
      int[] position = NodeArea.position(key, node, index);
      assertThat(position[0]).isBetween(-1, 5);
      assertThat(position[1]).isBetween(65, 67);
      assertThat(position[2]).isBetween(-4, 0);
      assertThat(NodeArea.covers(key, node, position[0] + 0.5D, position[1] + 0.5D, position[2] + 0.5D)).isTrue();
      seen.add(position[0] + "," + position[1] + "," + position[2]);
    }

    assertThat(NodeArea.volume(node)).isEqualTo(7 * 3 * 5);
    assertThat(seen).hasSize(7 * 3 * 5);
    assertThat(NodeArea.position(key, node, 0)).containsExactly(-1, 65, -4);
    assertThat(NodeArea.position(key, node, NodeArea.volume(node) - 1)).containsExactly(5, 67, 0);
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

  @Test
  void aRadiusThirtyTwoAreaIsScannedInFullAtTheSameBudgetPerPass() {
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(32, 32, 32)).build();
    int volume = NodeArea.volume(node);
    int budget = NodeSettings.defaults().blocksPerScan();
    NodeScanCursor cursor = new NodeScanCursor();
    int expectedStart = 0;
    int passes = 0;

    while (expectedStart < volume) {
      NodeScanCursor.Window window = cursor.next(budget, volume);
      assertThat(window.start()).isEqualTo(expectedStart);
      assertThat(window.count()).isBetween(1, budget);
      expectedStart += window.count();
      passes++;
    }

    assertThat(volume).isEqualTo(65 * 65 * 65).isPositive();
    assertThat(expectedStart).isEqualTo(volume);
    assertThat(passes).isEqualTo((volume + budget - 1) / budget);
    assertThat(cursor.next(budget, volume)).isEqualTo(new NodeScanCursor.Window(0, budget));
  }

  @Test
  void everyIndexOfARadiusThirtyTwoAreaMapsToItsOwnBlockWithoutOverflow() {
    NodeKey key = new NodeKey(WORLD, 29_999_900, 120, -29_999_900, BlockFace.UP);
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).area(NodeCuboid.symmetric(32, 32, 32)).build();
    int[] center = NodeArea.center(key);
    BitSet seen = new BitSet(NodeArea.volume(node));

    for (int index = 0; index < NodeArea.volume(node); index++) {
      int[] position = NodeArea.position(key, node, index);
      int offsetX = position[0] - (center[0] - 32);
      int offsetY = position[1] - (center[1] - 32);
      int offsetZ = position[2] - (center[2] - 32);
      assertThat(NodeArea.covers(key, node, position[0] + 0.5D, position[1] + 0.5D, position[2] + 0.5D)).isTrue();
      seen.set((offsetY * 65 + offsetZ) * 65 + offsetX);
    }

    assertThat(seen.cardinality()).isEqualTo(NodeArea.volume(node));
    assertThat(NodeArea.position(key, node, 0)).containsExactly(center[0] - 32, center[1] - 32, center[2] - 32);
    assertThat(NodeArea.position(key, node, NodeArea.volume(node) - 1)).containsExactly(center[0] + 32, center[1] + 32, center[2] + 32);
  }
}
