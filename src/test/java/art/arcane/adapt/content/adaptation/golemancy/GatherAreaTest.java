package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatherAreaTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  private final World world = world(WORLD);
  private final World nether = world(UUID.fromString("00000000-0000-0000-0000-0000000000c3"));

  @Test
  void theHomeAreaIsTheSphereOfThePickupRadius() {
    GatherArea area = GatherArea.home(new Location(world, 0.5D, 64D, 0.5D), 4D);

    assertThat(area.byNodes()).isFalse();
    assertThat(area.contains(new Location(world, 4.4D, 64D, 0.5D))).isTrue();
    assertThat(area.contains(new Location(world, 4.6D, 64D, 0.5D))).isFalse();
    assertThat(area.contains(new Location(world, 0.5D, 68.6D, 0.5D))).isFalse();
    assertThat(area.contains(new Location(nether, 0.5D, 64D, 0.5D))).isFalse();
  }

  @Test
  void aNodeAreaIsTheBlockBoxAroundTheBlockInFrontOfTheFace() {
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(2, 1, 3)).build();
    GatherArea.Box box = GatherArea.Box.of(key, record);
    GatherArea area = GatherArea.nodes(WORLD, List.of(box));

    assertThat(area.byNodes()).isTrue();
    assertThat(area.contains(new Location(world, 0.5D, 65D, 0.5D))).isTrue();
    assertThat(area.contains(new Location(world, 2.9D, 66.5D, -2.5D))).isTrue();
    assertThat(area.contains(new Location(world, -1.5D, 64D, 3.99D))).isTrue();
    assertThat(area.contains(new Location(world, 3D, 65D, 0.5D))).isFalse();
    assertThat(area.contains(new Location(world, 0.5D, 63.99D, 0.5D))).isFalse();
    assertThat(area.contains(new Location(nether, 0.5D, 65D, 0.5D))).isFalse();
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
    assertThat(area.contains(new Location(world, 5.9D, 67.5D, -3.5D))).isTrue();
    assertThat(area.contains(new Location(world, -0.5D, 65D, 0.5D))).isTrue();
    assertThat(area.contains(new Location(world, -1.5D, 65D, 0.5D))).isFalse();
    assertThat(area.contains(new Location(world, 0.5D, 65D, 1D))).isFalse();
    assertThat(area.contains(new Location(world, 0.5D, 64.99D, 0.5D))).isFalse();
    assertThat(box.centerX()).isEqualTo(2.5D);
    assertThat(box.centerZ()).isEqualTo(-1.5D);
    assertThat(box.halfX()).isEqualTo(3.5D);
    assertThat(box.halfZ()).isEqualTo(2.5D);
  }

  @Test
  void aNodeAreaCollectsOnlyWhatItsNodeFilterAccepts() {
    NodeRecord carrots = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1))
        .filterItems(List.of(GolemTestItems.stack(Material.CARROT, 1))).build();
    NodeRecord anything = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1)).build();
    GatherArea area = GatherArea.nodes(WORLD, List.of(GatherArea.Box.of(new NodeKey(WORLD, 0, 64, 0, BlockFace.UP), carrots),
        GatherArea.Box.of(new NodeKey(WORLD, 20, 64, 0, BlockFace.UP), anything)));
    GatherArea home = GatherArea.home(new Location(world, 0.5D, 64D, 0.5D), 4D);

    assertThat(area.collects(new Location(world, 0.5D, 65D, 0.5D), Material.CARROT)).isTrue();
    assertThat(area.collects(new Location(world, 0.5D, 65D, 0.5D), Material.WHEAT)).isFalse();
    assertThat(area.collects(new Location(world, 20.5D, 65D, 0.5D), Material.WHEAT)).isTrue();
    assertThat(area.collects(new Location(world, 10.5D, 65D, 0.5D), Material.CARROT)).isFalse();
    assertThat(home.collects(new Location(world, 1.5D, 64D, 0.5D), Material.WHEAT)).isTrue();
  }

  @Test
  void aNodeAreaWithNoActiveNodesCoversNothing() {
    GatherArea area = GatherArea.nodes(WORLD, List.of());

    assertThat(area.byNodes()).isTrue();
    assertThat(area.contains(new Location(world, 0.5D, 64D, 0.5D))).isFalse();
    assertThat(area.collects(new Location(world, 0.5D, 64D, 0.5D), Material.WHEAT)).isFalse();
  }

  @Test
  void severalNodeAreasCoverTheirUnion() {
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).area(NodeCuboid.symmetric(1, 1, 1)).build();
    GatherArea area = GatherArea.nodes(WORLD, List.of(GatherArea.Box.of(new NodeKey(WORLD, 0, 64, 0, BlockFace.UP), record),
        GatherArea.Box.of(new NodeKey(WORLD, 20, 64, 0, BlockFace.UP), record)));

    assertThat(area.contains(new Location(world, 0.5D, 65D, 0.5D))).isTrue();
    assertThat(area.contains(new Location(world, 20.5D, 65D, 0.5D))).isTrue();
    assertThat(area.contains(new Location(world, 10.5D, 65D, 0.5D))).isFalse();
  }

  private static World world(UUID id) {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(id);
    return world;
  }
}
