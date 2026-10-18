package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Location;
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
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).radii(2, 1, 3).build();
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
  void severalNodeAreasCoverTheirUnion() {
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP).radii(1, 1, 1).build();
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
