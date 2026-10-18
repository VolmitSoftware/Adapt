package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.fx.display.HiddenDisplays;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NodeIconsTest {
  private static final float EPSILON = 1.0E-6F;

  @Test
  void eachNodeShowsItsRecipeIcon() {
    Map<NodeType, Material> icons = new EnumMap<>(NodeType.class);
    for (NodeType type : NodeType.values()) {
      icons.put(type, NodeIcons.icon(type));
    }

    assertThat(icons).containsExactlyInAnyOrderEntriesOf(Map.of(
        NodeType.GUARD, Material.IRON_SWORD,
        NodeType.COLLECTION, Material.BUNDLE,
        NodeType.DEPOSIT, Material.CHEST,
        NodeType.HARVEST, Material.IRON_HOE,
        NodeType.LUMBER, Material.IRON_AXE,
        NodeType.BREAKER, Material.IRON_PICKAXE,
        NodeType.PASTURE, Material.LEAD,
        NodeType.INPUT, Material.COPPER_CHEST,
        NodeType.FILTER, Material.ITEM_FRAME,
        NodeType.FISHING, Material.FISHING_ROD));
  }

  @Test
  void chestModelsRenderFourteenSixteenthsDeepAtHalfFixedScale() {
    float chestHalfDepth = (14F / 16F) * 0.5F * NodeIcons.ICON_SCALE * 0.5F;

    assertThat(NodeIcons.halfDepth(Material.CHEST)).isCloseTo(chestHalfDepth, within(EPSILON));
    assertThat(NodeIcons.halfDepth(Material.COPPER_CHEST)).isCloseTo(chestHalfDepth, within(EPSILON));
    assertThat(chestHalfDepth).isCloseTo(0.109375F, within(EPSILON));
  }

  @Test
  void flatSpritesIncludingItemFramesAndHoppersRenderOneSixteenthDeep() {
    float spriteHalfDepth = (1F / 16F) * NodeIcons.ICON_SCALE * 0.5F;

    for (Material flat : new Material[]{Material.IRON_SWORD, Material.BUNDLE, Material.ITEM_FRAME, Material.HOPPER, Material.LEAD, Material.FISHING_ROD}) {
      assertThat(NodeIcons.halfDepth(flat)).as(flat.name()).isCloseTo(spriteHalfDepth, within(EPSILON));
    }
  }

  @ParameterizedTest
  @EnumSource(value = NodeType.class)
  void theBackOfEveryIconSitsJustOffTheAnchorFace(NodeType type) {
    Material icon = NodeIcons.icon(type);
    for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
      Transformation transformation = HiddenDisplays.faceTransformation(face, NodeIcons.ICON_SCALE, NodeIcons.faceOffset(icon));
      Vector3f normal = new Vector3f(face.getModX(), face.getModY(), face.getModZ());

      float backPlane = transformation.getTranslation().dot(normal) - NodeIcons.halfDepth(icon) - 0.5F;

      assertThat(backPlane).as("%s on %s", type, face).isCloseTo(HiddenDisplays.SURFACE_OFFSET, within(EPSILON));
    }
  }

  @Test
  void theDepthOffsetOnlyPushesOutward() {
    Vector3f offset = NodeIcons.faceOffset(Material.CHEST);

    assertThat(offset.x).isZero();
    assertThat(offset.y).isZero();
    assertThat(offset.z).isCloseTo(NodeIcons.halfDepth(Material.CHEST), within(EPSILON));
  }
}
