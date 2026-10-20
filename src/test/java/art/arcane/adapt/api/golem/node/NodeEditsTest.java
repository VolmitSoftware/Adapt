package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeEditsTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  @Test
  void theChannelCyclesThroughEveryDyeAndBackToNone() {
    assertThat(NodeEdits.nextChannel(null, true)).isEqualTo(DyeColor.WHITE);
    assertThat(NodeEdits.nextChannel(DyeColor.WHITE, true)).isEqualTo(DyeColor.ORANGE);
    assertThat(NodeEdits.nextChannel(DyeColor.BLACK, true)).isNull();
    assertThat(NodeEdits.nextChannel(null, false)).isEqualTo(DyeColor.BLACK);
    assertThat(NodeEdits.nextChannel(DyeColor.WHITE, false)).isNull();
    List<DyeColor> visited = new ArrayList<>();
    DyeColor channel = null;
    for (int step = 0; step <= DyeColor.values().length; step++) {
      channel = NodeEdits.nextChannel(channel, true);
      visited.add(channel);
    }
    assertThat(visited.getLast()).isNull();
    assertThat(visited.subList(0, DyeColor.values().length)).containsExactly(DyeColor.values());
  }

  @Test
  void everyDyeSetsItsOwnChannelAndOtherItemsSetNone() {
    for (DyeColor color : DyeColor.values()) {
      assertThat(NodeEdits.dyeChannel(Material.valueOf(color.name() + "_DYE"))).as(color.name()).contains(color);
    }
    assertThat(NodeEdits.dyeChannel(Material.INK_SAC)).isEmpty();
    assertThat(NodeEdits.dyeChannel(Material.COBBLESTONE)).isEmpty();
    assertThat(NodeEdits.dyeChannel(null)).isEmpty();
  }

  @Test
  void priorityCyclesOnlyFromNodeWeavingLevelThree() {
    NodeRecord record = record().priority(3).build();

    assertThat(NodeEdits.cyclePriority(record, 0, true)).isEmpty();
    assertThat(NodeEdits.cyclePriority(record, 2, true)).isEmpty();
    assertThat(NodeEdits.cyclePriority(record, 3, true)).map(NodeRecord::priority).contains(4);
    assertThat(NodeEdits.cyclePriority(record, 5, false)).map(NodeRecord::priority).contains(2);
  }

  @Test
  void priorityWrapsBetweenOneAndFive() {
    assertThat(NodeEdits.nextPriority(5, true)).isEqualTo(1);
    assertThat(NodeEdits.nextPriority(1, false)).isEqualTo(5);
    assertThat(NodeEdits.nextPriority(2, true)).isEqualTo(3);
  }

  @Test
  void resizingAddsOneBlockAndSupportsEverySizeThroughSixteen() {
    NodeRecord record = record().area(NodeCuboid.symmetric(0, 0, 0)).build();
    for (int size = 2; size <= 16; size++) {
      record = NodeEdits.resize(record, NodeEdits.Axis.X, NodeEdits.STEP, 32);
      assertThat(record.area().spanX()).isEqualTo(size);
      assertThat(record.area().contains(0, 0, 0)).isTrue();
    }
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, 1, 32)).isSameAs(record);
    for (int size = 15; size >= 1; size--) {
      record = NodeEdits.resize(record, NodeEdits.Axis.X, -NodeEdits.STEP, 32);
      assertThat(record.area().spanX()).isEqualTo(size);
      assertThat(record.area().contains(0, 0, 0)).isTrue();
    }
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, -1, 32)).isSameAs(record);
  }

  @Test
  void shiftClickAddsFourTotalBlocksWhileRespectingSizeAndReach() {
    NodeRecord record = record().build();
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, 4, 32).area().spanX()).isEqualTo(9);
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, 4, 2)).isSameAs(record);
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, -4, 32).area().spanX()).isEqualTo(1);
    NodeRecord maximum = record().area(new NodeCuboid(-8, -8, -8, 7, 7, 7)).build();
    assertThat(NodeEdits.resize(maximum, NodeEdits.Axis.Y, 4, 32)).isSameAs(maximum);
  }

  @Test
  void lopsidedAreasGrowTowardTheNodeAndShrinkFromTheFartherFace() {
    NodeRecord record = record().area(new NodeCuboid(-1, 0, -5, 4, 2, 0)).build();
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, 1, 8).area()).isEqualTo(new NodeCuboid(-2, 0, -5, 4, 2, 0));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, -1, 8).area()).isEqualTo(new NodeCuboid(-1, 0, -5, 3, 2, 0));
    NodeRecord overCap = record().area(NodeCuboid.symmetric(6, 6, 6)).build();
    assertThat(NodeEdits.resize(overCap, NodeEdits.Axis.X, 1, 2)).isSameAs(overCap);
    assertThat(NodeEdits.resize(overCap, NodeEdits.Axis.X, -1, 2).area()).isEqualTo(new NodeCuboid(-6, -6, -6, 5, 6, 6));
  }

  @Test
  void redstoneModesCycleInBothDirections() {
    assertThat(NodeEdits.nextRedstone(RedstoneMode.IGNORE, true)).isEqualTo(RedstoneMode.POWERED);
    assertThat(NodeEdits.nextRedstone(RedstoneMode.POWERED, true)).isEqualTo(RedstoneMode.UNPOWERED);
    assertThat(NodeEdits.nextRedstone(RedstoneMode.UNPOWERED, true)).isEqualTo(RedstoneMode.IGNORE);
    assertThat(NodeEdits.nextRedstone(RedstoneMode.IGNORE, false)).isEqualTo(RedstoneMode.UNPOWERED);
  }

  private static NodeRecord.Builder record() {
    return NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP);
  }
}
