package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.fx.ViewerDisplayPort;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeOutlineTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

  @Test
  void nearbyOwnedBoxesRenderPrivateStretchedGlassAndClearAfterRemoval() {
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(WORLD);
    PlayerRef viewer = mock(PlayerRef.class);
    when(viewer.uuid()).thenReturn(OWNER);
    when(viewer.world()).thenReturn(world);
    when(viewer.location()).thenReturn(new Loc(world, 0.5D, 65D, 0.5D));
    NodeKey key = new NodeKey(WORLD, 0, 64, 0, BlockFace.UP);
    NodeKey other = new NodeKey(WORLD, 1, 64, 0, BlockFace.UP);
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).channel(DyeColor.CYAN).build();
    NodeIndex index = new NodeIndex();
    index.completeLoad(WORLD, key.chunkKey(), index.beginLoad(WORLD, key.chunkKey()), Map.of(key, node,
        other, NodeRecord.builder(UUID.randomUUID(), NodeType.HARVEST, BlockFace.UP).build()));
    BlockStateRef glass = mock(BlockStateRef.class);
    AtomicLong clock = new AtomicLong(1_000L);
    NodeOutline outline = new NodeOutline(color -> glass, clock::get);
    ViewerDisplayPort displays = mock(ViewerDisplayPort.class);
    AdaptPorts.install(ViewerDisplayPort.class, displays);
    try {
      outline.update(viewer, index, 10);
      outline.update(viewer, index, 10);
      verify(displays, times(2)).showCuboid(eq(NodeOutline.CHANNEL), anyString(), eq(viewer),
          eq(new Loc(world, -2D, 63D, -2D)), eq(new Vec3(5D, 5D, 5D)), eq(glass), isNull(), anyBoolean(), eq(NodeOutline.DURATION_TICKS));
      clock.addAndGet(9_000L);
      outline.update(viewer, index, 10);
      verify(displays, times(4)).showCuboid(anyString(), anyString(), any(PlayerRef.class), any(Loc.class),
          any(Vec3.class), any(BlockStateRef.class), isNull(), anyBoolean(), anyInt());
      index.remove(key);
      outline.update(viewer, index, 10);
      verify(displays, times(6)).clearViewerKey(eq(NodeOutline.CHANNEL), anyString(), eq(OWNER));
      outline.forget(OWNER);
      verify(displays).clearViewer(NodeOutline.CHANNEL, OWNER);
    } finally {
      AdaptPorts.uninstall(ViewerDisplayPort.class);
    }
  }
}
