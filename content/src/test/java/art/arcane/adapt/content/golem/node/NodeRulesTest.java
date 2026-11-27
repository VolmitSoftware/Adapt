package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NodeRulesTest {
  private static final NodePlacementCheck READY = new NodePlacementCheck(true, true, true, true, true, false, false, false, 3, 16, 2, 4);

  @Test
  void aCleanPlacementIsReady() {
    assertThat(NodeRules.placement(READY)).isEqualTo(NodePlacementVerdict.READY);
  }

  @Test
  void placementChecksFailInPriorityOrder() {
    assertThat(NodeRules.placement(new NodePlacementCheck(false, false, false, false, true, true, false, false, 99, 16, 99, 4)))
        .isEqualTo(NodePlacementVerdict.UNAVAILABLE);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, false, false, false, true, true, false, false, 99, 16, 99, 4)))
        .isEqualTo(NodePlacementVerdict.NOT_LOADED);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, false, false, true, true, false, false, 99, 16, 99, 4)))
        .isEqualTo(NodePlacementVerdict.OCCUPIED);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, false, true, true, false, false, 99, 16, 99, 4)))
        .isEqualTo(NodePlacementVerdict.PROTECTED);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, true, false, true, false, false, 99, 16, 99, 4)))
        .isEqualTo(NodePlacementVerdict.NEEDS_SIDE_FACE);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, true, true, true, false, false, 99, 16, 99, 4)))
        .isEqualTo(NodePlacementVerdict.NEEDS_CONTAINER);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, true, true, true, true, false, 99, 16, 99, 4)))
        .isEqualTo(NodePlacementVerdict.CONTAINER_PROTECTED);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, true, true, true, true, true, 16, 16, 99, 4)))
        .isEqualTo(NodePlacementVerdict.CHUNK_FULL);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, true, true, true, true, true, 15, 16, 4, 4)))
        .isEqualTo(NodePlacementVerdict.NODE_CAP);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, true, true, true, true, true, 15, 16, 3, 4)))
        .isEqualTo(NodePlacementVerdict.READY);
  }

  @Test
  void containerChecksOnlyApplyToContainerNodes() {
    NodePlacementCheck plainNodeOnStone = new NodePlacementCheck(true, true, true, true, true, false, false, false, 0, 16, 0, 4);

    assertThat(NodeRules.placement(plainNodeOnStone)).isEqualTo(NodePlacementVerdict.READY);
  }

  @Test
  void aZeroCapRefusesEveryPlacement() {
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, true, true, false, false, false, 0, 16, 0, 0)))
        .isEqualTo(NodePlacementVerdict.NODE_CAP);
    assertThat(NodeRules.placement(new NodePlacementCheck(true, true, true, true, true, false, false, false, 0, 0, 0, 4)))
        .isEqualTo(NodePlacementVerdict.CHUNK_FULL);
  }

  @Test
  void fishingNodesGoOnlyOnTheSideOfABlock() {
    for (NodeType type : NodeType.values()) {
      for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
        assertThat(NodeRules.faceAllowed(type, face)).as("%s on %s", type, face).isTrue();
      }
      boolean vertical = type != NodeType.FISHING;
      assertThat(NodeRules.faceAllowed(type, BlockFace.UP)).as("%s on UP", type).isEqualTo(vertical);
      assertThat(NodeRules.faceAllowed(type, BlockFace.DOWN)).as("%s on DOWN", type).isEqualTo(vertical);
    }
  }

  @Test
  void depositInputAndFilterNodesNeedAContainer() {
    Set<NodeType> needing = EnumSet.noneOf(NodeType.class);
    for (NodeType type : NodeType.values()) {
      if (NodeRules.needsContainer(type)) {
        needing.add(type);
      }
    }

    assertThat(needing).containsExactlyInAnyOrder(NodeType.DEPOSIT, NodeType.INPUT, NodeType.FILTER);
  }

  @Test
  void chestsBarrelsCopperChestsAndShulkerBoxesAreContainers() {
    for (Id material : List.of(Id.minecraft("chest"), Id.minecraft("trapped_chest"), Id.minecraft("barrel"), Id.minecraft("shulker_box"),
        Id.minecraft("red_shulker_box"), Id.minecraft("copper_chest"), Id.minecraft("waxed_oxidized_copper_chest"), Id.minecraft("weathered_copper_chest"))) {
      assertThat(NodeRules.isContainer(material)).as(material.toString()).isTrue();
    }
    for (Id material : List.of(Id.minecraft("hopper"), Id.minecraft("furnace"), Id.minecraft("ender_chest"), Id.minecraft("dispenser"), Id.minecraft("stone"), Id.minecraft("air"))) {
      assertThat(NodeRules.isContainer(material)).as(material.toString()).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(value = NodeType.class, names = {"DEPOSIT", "INPUT", "FILTER"})
  void containerNodesLackTheirContainerWhenTheAnchorIsNotOne(NodeType type) {
    assertThat(NodeRules.lacksContainer(type, Id.minecraft("chest"))).isFalse();
    assertThat(NodeRules.lacksContainer(type, Id.minecraft("barrel"))).isFalse();
    assertThat(NodeRules.lacksContainer(type, Id.minecraft("red_shulker_box"))).isFalse();
    for (Id material : List.of(Id.minecraft("stone"), Id.minecraft("air"), Id.minecraft("water"), Id.minecraft("hopper"), Id.minecraft("furnace"))) {
      assertThat(NodeRules.lacksContainer(type, material)).as(material.toString()).isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(value = NodeType.class, names = {"GUARD", "COLLECTION", "HARVEST", "LUMBER", "BREAKER", "PASTURE", "FISHING"})
  void areaNodesNeverLackAContainer(NodeType type) {
    for (Id material : List.of(Id.minecraft("stone"), Id.minecraft("chest"), Id.minecraft("air"), Id.minecraft("cave_air"), Id.minecraft("water"), Id.minecraft("lava"), Id.minecraft("fire"))) {
      assertThat(NodeRules.lacksContainer(type, material)).as(material.toString()).isFalse();
    }
  }

  @Test
  void unlocksArriveAtTheirNodeWeavingLevels() {
    assertThat(NodeRules.allowsPriority(2)).isFalse();
    assertThat(NodeRules.allowsPriority(3)).isTrue();
    assertThat(NodeRules.allowsRedstone(3)).isFalse();
    assertThat(NodeRules.allowsRedstone(4)).isTrue();
    assertThat(NodeRules.allowsBlacklistAndTags(4)).isFalse();
    assertThat(NodeRules.allowsBlacklistAndTags(5)).isTrue();
  }

  @Test
  void redstoneModesGateTheNodeOnItsAnchorsPower() {
    assertThat(NodeRules.active(RedstoneMode.IGNORE, NodePower.OFF)).isTrue();
    assertThat(NodeRules.active(RedstoneMode.IGNORE, NodePower.ON)).isTrue();
    assertThat(NodeRules.active(RedstoneMode.IGNORE, NodePower.UNKNOWN)).isTrue();
    assertThat(NodeRules.active(RedstoneMode.POWERED, NodePower.ON)).isTrue();
    assertThat(NodeRules.active(RedstoneMode.POWERED, NodePower.OFF)).isFalse();
    assertThat(NodeRules.active(RedstoneMode.POWERED, NodePower.UNKNOWN)).isFalse();
    assertThat(NodeRules.active(RedstoneMode.UNPOWERED, NodePower.OFF)).isTrue();
    assertThat(NodeRules.active(RedstoneMode.UNPOWERED, NodePower.ON)).isFalse();
    assertThat(NodeRules.active(RedstoneMode.UNPOWERED, NodePower.UNKNOWN)).isFalse();
  }
}
