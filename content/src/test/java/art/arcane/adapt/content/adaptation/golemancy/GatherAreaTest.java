package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeCuboid;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatherAreaTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  private final WorldRef world = world(WORLD);
  private final WorldRef nether = world(UUID.fromString("00000000-0000-0000-0000-0000000000c3"));

  @Test
  void theHomeAreaIsTheSphereOfThePickupRadius() {
    GatherArea area = GatherArea.home(new Loc(world, 0.5D, 64D, 0.5D), 4D);

    assertThat(area.byNodes()).isFalse();
    assertThat(area.contains(new Loc(world, 4.4D, 64D, 0.5D))).isTrue();
    assertThat(area.contains(new Loc(world, 4.6D, 64D, 0.5D))).isFalse();
    assertThat(area.contains(new Loc(world, 0.5D, 68.6D, 0.5D))).isFalse();
    assertThat(area.contains(new Loc(nether, 0.5D, 64D, 0.5D))).isFalse();
  }

  @Test
  void aNodeAreaIsTheBlockBoxAroundTheBlockInFrontOfTheFace() {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(2, 1, 3)).build();
    GatherArea.Box box = GatherArea.Box.of(key, record);
    GatherArea area = GatherArea.nodes(WORLD, List.of(box));

    assertThat(area.byNodes()).isTrue();
    assertThat(area.contains(new Loc(world, 0.5D, 65D, 0.5D))).isTrue();
    assertThat(area.contains(new Loc(world, 2.9D, 66.5D, -2.5D))).isTrue();
    assertThat(area.contains(new Loc(world, -1.5D, 64D, 3.99D))).isTrue();
    assertThat(area.contains(new Loc(world, 3D, 65D, 0.5D))).isFalse();
    assertThat(area.contains(new Loc(world, 0.5D, 63.99D, 0.5D))).isFalse();
    assertThat(area.contains(new Loc(nether, 0.5D, 65D, 0.5D))).isFalse();
    assertThat(box.centerX()).isEqualTo(0.5D);
    assertThat(box.centerY()).isEqualTo(65.5D);
    assertThat(box.centerZ()).isEqualTo(0.5D);
    assertThat(box.halfX()).isEqualTo(2.5D);
    assertThat(box.halfY()).isEqualTo(1.5D);
    assertThat(box.halfZ()).isEqualTo(3.5D);
  }

  @Test
  void aLopsidedNodeAreaIsTheBoxBetweenItsFaces() {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(new NodeCuboid(-1, 0, -4, 5, 2, 0)).build();
    GatherArea.Box box = GatherArea.Box.of(key, record);
    GatherArea area = GatherArea.nodes(WORLD, List.of(box));

    assertThat(box).isEqualTo(new GatherArea.Box(-1, 65, -4, 5, 67, 0, record));
    assertThat(area.contains(new Loc(world, 5.9D, 67.5D, -3.5D))).isTrue();
    assertThat(area.contains(new Loc(world, -0.5D, 65D, 0.5D))).isTrue();
    assertThat(area.contains(new Loc(world, -1.5D, 65D, 0.5D))).isFalse();
    assertThat(area.contains(new Loc(world, 0.5D, 65D, 1D))).isFalse();
    assertThat(area.contains(new Loc(world, 0.5D, 64.99D, 0.5D))).isFalse();
    assertThat(box.centerX()).isEqualTo(2.5D);
    assertThat(box.centerZ()).isEqualTo(-1.5D);
    assertThat(box.halfX()).isEqualTo(3.5D);
    assertThat(box.halfZ()).isEqualTo(2.5D);
  }

  @Test
  void aNodeAreaCollectsOnlyWhatItsNodeFilterAccepts() {
    NodeRecord carrots = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1))
        .filterItems(List.of(GolemTestItems.stack(Id.minecraft("carrot"), 1))).build();
    NodeRecord anything = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1)).build();
    GatherArea area = GatherArea.nodes(WORLD, List.of(GatherArea.Box.of(new NodeKey(WORLD, 0, 64, 0, BlockFace.UP), carrots),
        GatherArea.Box.of(new NodeKey(WORLD, 20, 64, 0, BlockFace.UP), anything)));
    GatherArea home = GatherArea.home(new Loc(world, 0.5D, 64D, 0.5D), 4D);

    assertThat(area.collects(new Loc(world, 0.5D, 65D, 0.5D), Id.minecraft("carrot"))).isTrue();
    assertThat(area.collects(new Loc(world, 0.5D, 65D, 0.5D), Id.minecraft("wheat"))).isFalse();
    assertThat(area.collects(new Loc(world, 20.5D, 65D, 0.5D), Id.minecraft("wheat"))).isTrue();
    assertThat(area.collects(new Loc(world, 10.5D, 65D, 0.5D), Id.minecraft("carrot"))).isFalse();
    assertThat(home.collects(new Loc(world, 1.5D, 64D, 0.5D), Id.minecraft("wheat"))).isTrue();
  }

  @Test
  void aNodeAreaWithNoActiveNodesCoversNothing() {
    GatherArea area = GatherArea.nodes(WORLD, List.of());

    assertThat(area.byNodes()).isTrue();
    assertThat(area.contains(new Loc(world, 0.5D, 64D, 0.5D))).isFalse();
    assertThat(area.collects(new Loc(world, 0.5D, 64D, 0.5D), Id.minecraft("wheat"))).isFalse();
  }

  @Test
  void severalNodeAreasCoverTheirUnion() {
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1)).build();
    GatherArea area = GatherArea.nodes(WORLD, List.of(GatherArea.Box.of(new NodeKey(WORLD, 0, 64, 0, BlockFace.UP), record),
        GatherArea.Box.of(new NodeKey(WORLD, 20, 64, 0, BlockFace.UP), record)));

    assertThat(area.contains(new Loc(world, 0.5D, 65D, 0.5D))).isTrue();
    assertThat(area.contains(new Loc(world, 20.5D, 65D, 0.5D))).isTrue();
    assertThat(area.contains(new Loc(world, 10.5D, 65D, 0.5D))).isFalse();
  }

  private static WorldRef world(UUID id) {
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(id);
    return world;
  }
}
