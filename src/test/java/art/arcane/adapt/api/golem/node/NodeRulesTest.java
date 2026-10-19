package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
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
    for (Material material : List.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.SHULKER_BOX,
        Material.RED_SHULKER_BOX, Material.COPPER_CHEST, Material.WAXED_OXIDIZED_COPPER_CHEST, Material.WEATHERED_COPPER_CHEST)) {
      assertThat(NodeRules.isContainer(material)).as(material.name()).isTrue();
    }
    for (Material material : List.of(Material.HOPPER, Material.FURNACE, Material.ENDER_CHEST, Material.DISPENSER, Material.STONE, Material.AIR)) {
      assertThat(NodeRules.isContainer(material)).as(material.name()).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(value = NodeType.class, names = {"DEPOSIT", "INPUT", "FILTER"})
  void containerNodesDropWhenTheirContainerIsGoneOrReplaced(NodeType type) {
    assertThat(NodeRules.validate(type, Material.CHEST)).isEqualTo(NodeValidation.KEEP);
    assertThat(NodeRules.validate(type, Material.BARREL)).isEqualTo(NodeValidation.KEEP);
    assertThat(NodeRules.validate(type, Material.STONE)).isEqualTo(NodeValidation.DROP);
    assertThat(NodeRules.validate(type, Material.AIR)).isEqualTo(NodeValidation.DROP);
  }

  @ParameterizedTest
  @EnumSource(value = NodeType.class, names = {"GUARD", "COLLECTION", "HARVEST", "LUMBER", "BREAKER", "PASTURE", "FISHING"})
  void otherNodesDropOnlyWhenTheirAnchorIsGone(NodeType type) {
    assertThat(NodeRules.validate(type, Material.STONE)).isEqualTo(NodeValidation.KEEP);
    assertThat(NodeRules.validate(type, Material.CHEST)).isEqualTo(NodeValidation.KEEP);
    for (Material empty : List.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.WATER, Material.LAVA, Material.FIRE, Material.BUBBLE_COLUMN)) {
      assertThat(NodeRules.validate(type, empty)).as(empty.name()).isEqualTo(NodeValidation.DROP);
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
