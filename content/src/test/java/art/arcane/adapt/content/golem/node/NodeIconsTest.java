package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.content.golem.HiddenDisplays;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
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
    Map<NodeType, Id> icons = new EnumMap<>(NodeType.class);
    for (NodeType type : NodeType.values()) {
      icons.put(type, NodeIcons.icon(type));
    }

    assertThat(icons).containsExactlyInAnyOrderEntriesOf(Map.of(
        NodeType.GUARD, Id.minecraft("iron_sword"),
        NodeType.COLLECTION, Id.minecraft("bundle"),
        NodeType.DEPOSIT, Id.minecraft("chest"),
        NodeType.HARVEST, Id.minecraft("iron_hoe"),
        NodeType.LUMBER, Id.minecraft("iron_axe"),
        NodeType.BREAKER, Id.minecraft("iron_pickaxe"),
        NodeType.PASTURE, Id.minecraft("lead"),
        NodeType.INPUT, Id.minecraft("copper_chest"),
        NodeType.FILTER, Id.minecraft("item_frame"),
        NodeType.FISHING, Id.minecraft("fishing_rod")));
  }

  @Test
  void chestModelsRenderFourteenSixteenthsDeepAtHalfFixedScale() {
    float chestHalfDepth = (14F / 16F) * 0.5F * NodeIcons.ICON_SCALE * 0.5F;

    assertThat(NodeIcons.halfDepth(Id.minecraft("chest"))).isCloseTo(chestHalfDepth, within(EPSILON));
    assertThat(NodeIcons.halfDepth(Id.minecraft("copper_chest"))).isCloseTo(chestHalfDepth, within(EPSILON));
    assertThat(chestHalfDepth).isCloseTo(0.109375F, within(EPSILON));
  }

  @Test
  void flatSpritesIncludingItemFramesAndHoppersRenderOneSixteenthDeep() {
    float spriteHalfDepth = (1F / 16F) * NodeIcons.ICON_SCALE * 0.5F;

    for (Id flat : new Id[]{Id.minecraft("iron_sword"), Id.minecraft("bundle"), Id.minecraft("item_frame"), Id.minecraft("hopper"), Id.minecraft("lead"), Id.minecraft("fishing_rod")}) {
      assertThat(NodeIcons.halfDepth(flat)).as(flat.toString()).isCloseTo(spriteHalfDepth, within(EPSILON));
    }
  }

  @ParameterizedTest
  @EnumSource(value = NodeType.class)
  void theBackOfEveryIconSitsJustOffTheAnchorFace(NodeType type) {
    Id icon = NodeIcons.icon(type);
    for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
      Transform transformation = HiddenDisplays.faceTransformation(face, NodeIcons.ICON_SCALE, NodeIcons.faceOffset(icon));
      Vec3 normal = new Vec3(face.dx(), face.dy(), face.dz());

      float backPlane = (float) transformation.translation().dot(normal) - NodeIcons.halfDepth(icon) - 0.5F;

      assertThat(backPlane).as("%s on %s", type, face).isCloseTo(HiddenDisplays.SURFACE_OFFSET, within(EPSILON));
    }
  }

  @Test
  void theDepthOffsetOnlyPushesOutward() {
    Vec3 offset = NodeIcons.faceOffset(Id.minecraft("chest"));

    assertThat(offset.x()).isZero();
    assertThat(offset.y()).isZero();
    assertThat((float) offset.z()).isCloseTo(NodeIcons.halfDepth(Id.minecraft("chest")), within(EPSILON));
  }
}
