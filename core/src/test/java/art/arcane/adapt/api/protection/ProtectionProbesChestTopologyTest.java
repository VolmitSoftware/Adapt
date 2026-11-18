package art.arcane.adapt.api.protection;

import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionProbesChestTopologyTest {
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
      assertConnectedNeighbor("left", entry.getKey(), entry.getValue());
    }
    for (Map.Entry<BlockFace, BlockFace> entry : COUNTER_CLOCKWISE.entrySet()) {
      assertConnectedNeighbor("right", entry.getKey(), entry.getValue());
    }
  }

  @Test
  void singleChestHasNoConnectedNeighbor() {
    BlockRef target = mock(BlockRef.class);
    BlockStateRef data = mock(BlockStateRef.class);
    when(target.type()).thenReturn(Id.minecraft("chest"));
    when(target.state()).thenReturn(data);
    when(data.property("type")).thenReturn("single");
    when(data.property("facing")).thenReturn("north");

    assertThat(ProtectionProbes.containerBlocks(target, false)).containsExactly(target);
    verify(target, never()).relative(any(BlockFace.class));
  }

  private static void assertConnectedNeighbor(String type, BlockFace facing, BlockFace expectedFace) {
    BlockRef target = mock(BlockRef.class);
    BlockRef neighbor = mock(BlockRef.class);
    BlockStateRef data = mock(BlockStateRef.class);
    when(target.type()).thenReturn(Id.minecraft("chest"));
    when(target.state()).thenReturn(data);
    when(data.property("type")).thenReturn(type);
    when(data.property("facing")).thenReturn(facing.name().toLowerCase(Locale.ROOT));
    when(target.relative(expectedFace)).thenReturn(neighbor);

    List<BlockRef> blocks = ProtectionProbes.containerBlocks(target, false);

    assertThat(blocks).containsExactly(target, neighbor);
    verify(target).relative(expectedFace);
  }
}
