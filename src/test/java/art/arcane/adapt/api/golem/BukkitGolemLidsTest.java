package art.arcane.adapt.api.golem;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BukkitGolemLidsTest {
  @Test
  void liddedContainersOpenAndCloseTheirLiveState() {
    Chest chest = mock(Chest.class);
    Block block = block(chest);

    BukkitGolemLids.apply(block, true);
    BukkitGolemLids.apply(block, false);

    verify(chest).open();
    verify(chest).close();
    verify(block, never()).getState();
  }

  @Test
  void containersWithoutALidAreLeftAlone() {
    BukkitGolemLids.apply(block(mock(BlockState.class)), true);
  }

  private static Block block(BlockState state) {
    Block block = mock(Block.class);
    lenient().when(block.getState(false)).thenReturn(state);
    return block;
  }
}
