package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeOutlineTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final NodeKey KEY = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);

  private final NodeIndex index = new NodeIndex();
  private final AtomicLong clock = new AtomicLong(1_000L);
  private MockedStatic<ViewerDisplayDirector> director;
  private World world;
  private Player viewer;
  private NodeOutline outline;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    viewer = mock(Player.class);
    lenient().when(viewer.getUniqueId()).thenReturn(OWNER);
    lenient().when(viewer.getWorld()).thenReturn(world);
    lenient().when(viewer.getEyeLocation()).thenReturn(new Location(world, 0.5D, 66.6D, 2.5D, 180F, 45F));
    director = mockStatic(ViewerDisplayDirector.class);
    director.when(() -> ViewerDisplayDirector.showLine(anyString(), anyString(), any(Player.class), any(Location.class), any(Location.class),
        any(BlockData.class), any(Color.class), anyDouble(), anyInt())).thenReturn(true);
    long chunk = GolemRoster.chunkKey(0, 0);
    index.completeLoad(WORLD, chunk, index.beginLoad(WORLD, chunk), Map.of());
    outline = new NodeOutline(color -> mock(BlockData.class), clock::get);
  }

  @AfterEach
  void tearDown() {
    director.close();
  }

  @Test
  void theOutlineTracesTheTwelveEdgesOfTheWorkArea() {
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(1, 0, 2).build();

    List<NodeOutline.Edge> edges = NodeOutline.edges(KEY, record);

    assertThat(edges).hasSize(12);
    Set<Vector> corners = new HashSet<>();
    int alongX = 0;
    int alongY = 0;
    int alongZ = 0;
    for (NodeOutline.Edge edge : edges) {
      corners.add(edge.start());
      corners.add(edge.end());
      Vector delta = edge.end().clone().subtract(edge.start());
      if (delta.getX() != 0D) {
        alongX++;
        assertThat(delta.getX()).isEqualTo(3D);
      }
      if (delta.getY() != 0D) {
        alongY++;
        assertThat(delta.getY()).isEqualTo(1D);
      }
      if (delta.getZ() != 0D) {
        alongZ++;
        assertThat(delta.getZ()).isEqualTo(5D);
      }
    }
    assertThat(List.of(alongX, alongY, alongZ)).containsExactly(4, 4, 4);
    assertThat(corners).hasSize(8).contains(new Vector(-1, 65, -2), new Vector(2, 66, 3));
  }

  @Test
  void aRadiusThirtyTwoAreaStillTracesTwelveEdgesOfSixtyFiveBlocks() {
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(32, 32, 32).build();

    List<NodeOutline.Edge> edges = NodeOutline.edges(KEY, record);

    assertThat(edges).hasSize(12);
    Set<Vector> corners = new HashSet<>();
    for (NodeOutline.Edge edge : edges) {
      corners.add(edge.start());
      corners.add(edge.end());
      assertThat(edge.end().clone().subtract(edge.start()).length()).isEqualTo(65D);
    }
    assertThat(corners).hasSize(8).contains(new Vector(-32, 33, -32), new Vector(33, 98, 33));
  }

  @Test
  void lookingAtARadiusThirtyTwoNodeDrawsTheSameTwelveLines() {
    index.put(KEY, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(32, 32, 32).build());
    lookAt(0, 64, 0, BlockFace.UP);

    outline.update(viewer, index, 10);

    verifyLines(12);
  }

  @Test
  void lookingAtYourOwnNodeDrawsItsOutlineForYouAlone() {
    index.put(KEY, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).channel(DyeColor.LIME).build());
    lookAt(0, 64, 0, BlockFace.UP);

    outline.update(viewer, index, 10);

    director.verify(() -> ViewerDisplayDirector.showLine(eq(NodeOutline.CHANNEL), anyString(), eq(viewer), any(Location.class), any(Location.class),
        any(BlockData.class), eq(DyeColor.LIME.getColor()), anyDouble(), eq(NodeOutline.DURATION_TICKS)), times(12));
  }

  @Test
  void anUnchangedOutlineIsRedrawnOnlyWhenItNearsExpiry() {
    index.put(KEY, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build());
    lookAt(0, 64, 0, BlockFace.UP);
    outline.update(viewer, index, 10);

    clock.addAndGet((NodeOutline.DURATION_TICKS - 21) * 50L);
    outline.update(viewer, index, 10);
    verifyLines(12);

    clock.addAndGet(50L);
    outline.update(viewer, index, 10);
    verifyLines(24);
    director.verify(() -> ViewerDisplayDirector.clearViewer(NodeOutline.CHANNEL, OWNER), never());
  }

  @Test
  void aChangedAreaClearsAndRedrawsAtOnce() {
    index.put(KEY, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build());
    lookAt(0, 64, 0, BlockFace.UP);
    outline.update(viewer, index, 10);

    index.put(KEY, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(3, 2, 2).build());
    outline.update(viewer, index, 10);

    director.verify(() -> ViewerDisplayDirector.clearViewer(NodeOutline.CHANNEL, OWNER), times(1));
    verifyLines(24);
  }

  @Test
  void aSettingThatLeavesTheOutlineAloneDoesNotRedrawIt() {
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build();
    index.put(KEY, record);
    lookAt(0, 64, 0, BlockFace.UP);
    outline.update(viewer, index, 10);

    index.put(KEY, record.toBuilder().priority(5).whitelist(false).build());
    outline.update(viewer, index, 10);

    verifyLines(12);
    director.verify(() -> ViewerDisplayDirector.clearViewer(NodeOutline.CHANNEL, OWNER), never());
  }

  @Test
  void theRayIgnoresFluidsAndPassableBlocks() {
    index.put(KEY, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build());
    lookAt(0, 64, 0, BlockFace.UP);

    outline.update(viewer, index, 10);

    verify(world).rayTraceBlocks(any(Location.class), any(Vector.class), eq(NodeOutline.LOOK_DISTANCE), eq(FluidCollisionMode.NEVER), eq(true));
  }

  @Test
  void anotherPlayersNodeOrAnEmptyFaceDrawsNothing() {
    index.put(KEY, NodeRecord.builder(STRANGER, NodeType.HARVEST, BlockFace.UP).build());
    lookAt(0, 64, 0, BlockFace.UP);
    outline.update(viewer, index, 10);
    lookAt(0, 64, 0, BlockFace.NORTH);
    outline.update(viewer, index, 10);

    verifyLines(0);
  }

  @Test
  void lookingAwayClearsTheOutlineAtOnce() {
    index.put(KEY, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build());
    lookAt(0, 64, 0, BlockFace.UP);
    outline.update(viewer, index, 10);
    when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), anyBoolean())).thenReturn(null);

    outline.update(viewer, index, 10);
    outline.update(viewer, index, 10);

    director.verify(() -> ViewerDisplayDirector.clearViewer(NodeOutline.CHANNEL, OWNER), times(1));
  }

  private void verifyLines(int count) {
    director.verify(() -> ViewerDisplayDirector.showLine(anyString(), anyString(), any(Player.class), any(Location.class), any(Location.class),
        any(BlockData.class), any(Color.class), anyDouble(), anyInt()), times(count));
  }

  private void lookAt(int x, int y, int z, BlockFace face) {
    Block block = mock(Block.class);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    RayTraceResult hit = new RayTraceResult(new Vector(x + 0.5D, y + 1D, z + 0.5D), block, face);
    when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), anyBoolean())).thenReturn(hit);
  }
}
