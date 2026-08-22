package art.arcane.adapt.util.common.plugin;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionEventProbeChestTopologyTest {
  private static final Map<BlockFace, BlockFace> CLOCKWISE = Map.of(
      BlockFace.NORTH, BlockFace.EAST,
      BlockFace.EAST, BlockFace.SOUTH,
      BlockFace.SOUTH, BlockFace.WEST,
      BlockFace.WEST, BlockFace.NORTH
  );
  private static final Map<BlockFace, BlockFace> COUNTER_CLOCKWISE = Map.of(
      BlockFace.NORTH, BlockFace.WEST,
      BlockFace.WEST, BlockFace.SOUTH,
      BlockFace.SOUTH, BlockFace.EAST,
      BlockFace.EAST, BlockFace.NORTH
  );

  @Test
  void everyDoubleChestFacingResolvesItsPhysicalNeighbor() {
    for (Map.Entry<BlockFace, BlockFace> entry : CLOCKWISE.entrySet()) {
      assertConnectedNeighbor(Chest.Type.LEFT, entry.getKey(), entry.getValue());
    }
    for (Map.Entry<BlockFace, BlockFace> entry : COUNTER_CLOCKWISE.entrySet()) {
      assertConnectedNeighbor(Chest.Type.RIGHT, entry.getKey(), entry.getValue());
    }
  }

  @Test
  void singleChestHasNoConnectedNeighbor() {
    Block target = mock(Block.class);
    Chest data = mock(Chest.class);
    when(target.getBlockData()).thenReturn(data);
    when(data.getType()).thenReturn(Chest.Type.SINGLE);

    assertThat(ProtectionEventProbe.containerBlocks(target, null)).containsExactly(target);
    verify(target, never()).getRelative(any(BlockFace.class));
  }

  private static void assertConnectedNeighbor(Chest.Type type, BlockFace facing, BlockFace expectedFace) {
    Block target = mock(Block.class);
    Block neighbor = mock(Block.class);
    Chest data = mock(Chest.class);
    when(target.getBlockData()).thenReturn(data);
    when(data.getType()).thenReturn(type);
    when(data.getFacing()).thenReturn(facing);
    when(target.getRelative(expectedFace)).thenReturn(neighbor);

    List<Block> blocks = ProtectionEventProbe.containerBlocks(target, null);

    assertThat(blocks).containsExactly(target, neighbor);
    verify(target).getRelative(expectedFace);
  }
}
