package art.arcane.adapt.api.data;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeMatter;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.spatial.matter.SpatialMatter;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class WorldDataNodeStorageTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  private final List<WorldData> opened = new ArrayList<>();
  private World world;

  @BeforeAll
  static void registerNodeMatter() {
    SpatialMatter.registerSliceType(new NodeMatter());
  }

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    lenient().when(world.getKey()).thenReturn(new NamespacedKey("minecraft", "overworld"));
    lenient().when(world.getName()).thenReturn("world");
    lenient().when(world.getMinHeight()).thenReturn(-64);
    lenient().when(world.getMaxHeight()).thenReturn(320);
    lenient().when(plugin.getDataFolder(any(String[].class))).thenAnswer(invocation -> {
      Object[] arguments = invocation.getArguments();
      File folder = dataFolder;
      for (Object argument : arguments) {
        folder = new File(folder, String.valueOf(argument));
      }
      folder.mkdirs();
      return folder;
    });
  }

  @AfterEach
  void tearDown() {
    for (WorldData data : opened) {
      data.unregister();
    }
  }

  @Test
  void negativeCoordinatesAndBlocksBelowZeroRoundTripThroughTheCoordinateOverloads() {
    WorldData data = open();
    NodeFaces deep = faces(BlockFace.UP);
    NodeFaces high = faces(BlockFace.NORTH);

    data.set(-17, -60, -33, deep);
    data.set(-1, 300, -1, high);

    assertThat(data.get(-17, -60, -33, NodeFaces.class)).isEqualTo(deep);
    assertThat(data.get(-1, 300, -1, NodeFaces.class)).isEqualTo(high);
    assertThat(data.get(-17, -59, -33, NodeFaces.class)).isNull();
    data.remove(-17, -60, -33, NodeFaces.class);
    assertThat(data.get(-17, -60, -33, NodeFaces.class)).isNull();
  }

  @Test
  void chunkIterationReportsWorldCoordinatesForNegativeChunksAndMinHeight() {
    WorldData data = open();
    data.set(-17, -60, -33, faces(BlockFace.UP));
    data.set(-1, 300, -1, faces(BlockFace.NORTH));

    assertThat(visit(data, -2, -3)).containsExactly("-17,-60,-33");
    assertThat(visit(data, -1, -1)).containsExactly("-1,300,-1");
    assertThat(visit(data, 0, 0)).isEmpty();
    assertThat(visit(data, 900, 900)).isEmpty();
  }

  @Test
  void aSavedPlateIsReadableByAFreshMantleBeforeTheFirstOneCloses() {
    WorldData data = open();
    NodeFaces stored = faces(BlockFace.EAST);
    data.set(-17, -60, -33, stored);

    assertThat(data.savePlate(-17, -33)).isTrue();

    WorldData fresh = open();
    assertThat(fresh.get(-17, -60, -33, NodeFaces.class)).isEqualTo(stored);
    assertThat(data.get(-17, -60, -33, NodeFaces.class)).isEqualTo(stored);
  }

  @Test
  void savingAPlateThatWasNeverLoadedSucceeds() {
    assertThat(open().savePlate(10_000, 10_000)).isTrue();
  }

  private WorldData open() {
    WorldData data = new WorldData(world);
    opened.add(data);
    return data;
  }

  private static List<String> visit(WorldData data, int chunkX, int chunkZ) {
    List<String> found = new ArrayList<>();
    data.iterateChunk(chunkX, chunkZ, NodeFaces.class, (x, y, z, value) -> found.add(x + "," + y + "," + z));
    return found;
  }

  private static NodeFaces faces(BlockFace face) {
    return NodeFaces.EMPTY.with(NodeRecord.builder(OWNER, NodeType.GUARD, face).build());
  }
}
