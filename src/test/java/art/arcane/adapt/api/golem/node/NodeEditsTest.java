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
  void theAreaGrowsUpToTheLevelCapAndShrinksToZero() {
    NodeRecord record = record().area(NodeCuboid.symmetric(2, 1, 0)).build();

    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, 1, 3).area()).isEqualTo(NodeCuboid.symmetric(3, 1, 0));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, 1, 2)).isSameAs(record);
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.Y, -1, 3).area()).isEqualTo(NodeCuboid.symmetric(2, 0, 0));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.Z, -1, 3)).isSameAs(record);
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.Z, 1, 3).area()).isEqualTo(NodeCuboid.symmetric(2, 1, 1));
  }

  @Test
  void shiftClickResizesByFourWithinTheLevelCap() {
    NodeRecord record = record().area(NodeCuboid.symmetric(2, 8, 30)).build();

    assertThat(NodeEdits.SHIFT_STEP).isEqualTo(4);
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, NodeEdits.SHIFT_STEP, 32).area()).isEqualTo(NodeCuboid.symmetric(6, 8, 30));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.Y, NodeEdits.SHIFT_STEP, 10).area()).isEqualTo(NodeCuboid.symmetric(2, 10, 30));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.Z, NodeEdits.SHIFT_STEP, 32).area()).isEqualTo(NodeCuboid.symmetric(2, 8, 32));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, -NodeEdits.SHIFT_STEP, 32).area()).isEqualTo(NodeCuboid.symmetric(0, 8, 30));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.Y, -NodeEdits.SHIFT_STEP, 32).area()).isEqualTo(NodeCuboid.symmetric(2, 4, 30));
  }

  @Test
  void theAxisButtonsMoveBothFacesOfALopsidedCuboid() {
    NodeRecord record = record().area(new NodeCuboid(-1, 0, -5, 4, 2, 0)).build();

    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, NodeEdits.STEP, 8).area()).isEqualTo(new NodeCuboid(-2, 0, -5, 5, 2, 0));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, -NodeEdits.STEP, 8).area()).isEqualTo(new NodeCuboid(0, 0, -5, 3, 2, 0));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, NodeEdits.SHIFT_STEP, 6).area()).isEqualTo(new NodeCuboid(-5, 0, -5, 6, 2, 0));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.Y, NodeEdits.SHIFT_STEP, 8).area()).isEqualTo(new NodeCuboid(-1, -4, -5, 4, 6, 0));
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.Z, -NodeEdits.SHIFT_STEP, 8).area()).isEqualTo(new NodeCuboid(-1, 0, -1, 4, 2, 0));
  }

  @Test
  void anAreaAboveALoweredCapCanShrinkButNeverGrow() {
    NodeRecord record = record().area(NodeCuboid.symmetric(6, 6, 6)).build();

    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, 1, 2)).isSameAs(record);
    assertThat(NodeEdits.resize(record, NodeEdits.Axis.X, -1, 2).area()).isEqualTo(NodeCuboid.symmetric(5, 6, 6));
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
