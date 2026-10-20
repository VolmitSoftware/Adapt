package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class NodeOutlineTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

  @Test
  void nearbyOwnedBoxesRenderPrivateStretchedGlassAndClearAfterRemoval() {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(WORLD);
    Player viewer = mock(Player.class);
    when(viewer.getUniqueId()).thenReturn(OWNER);
    when(viewer.getWorld()).thenReturn(world);
    when(viewer.getLocation()).thenReturn(new Location(world, 0.5D, 65D, 0.5D));
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeKey other = new NodeKey(WORLD, 1, 64, 0, BlockFace.UP);
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).channel(DyeColor.CYAN).build();
    NodeIndex index = new NodeIndex();
    index.completeLoad(WORLD, key.chunkKey(), index.beginLoad(WORLD, key.chunkKey()), Map.of(key, node,
        other, NodeRecord.builder(UUID.randomUUID(), NodeType.HARVEST, BlockFace.UP).build()));
    BlockData glass = mock(BlockData.class);
    AtomicLong clock = new AtomicLong(1_000L);
    NodeOutline outline = new NodeOutline(color -> glass, clock::get);
    try (MockedStatic<ViewerDisplayDirector> displays = mockStatic(ViewerDisplayDirector.class)) {
      outline.update(viewer, index, 10);
      outline.update(viewer, index, 10);
      displays.verify(() -> ViewerDisplayDirector.showCuboid(eq(NodeOutline.CHANNEL), anyString(), eq(viewer),
          eq(new Location(world, -2D, 63D, -2D)), eq(new Vector(5D, 5D, 5D)), eq(glass), isNull(), anyBoolean(), eq(NodeOutline.DURATION_TICKS)), times(2));
      clock.addAndGet(9_000L);
      outline.update(viewer, index, 10);
      displays.verify(() -> ViewerDisplayDirector.showCuboid(anyString(), anyString(), any(Player.class), any(Location.class),
          any(Vector.class), any(BlockData.class), isNull(), anyBoolean(), anyInt()), times(4));
      index.remove(key);
      outline.update(viewer, index, 10);
      displays.verify(() -> ViewerDisplayDirector.clearViewerKey(eq(NodeOutline.CHANNEL), anyString(), eq(OWNER)), times(6));
      outline.forget(OWNER);
      displays.verify(() -> ViewerDisplayDirector.clearViewer(NodeOutline.CHANNEL, OWNER));
    }
  }
}
