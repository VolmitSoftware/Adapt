package art.arcane.adapt.api.golem;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    Hopper hopper = mock(Hopper.class);
    Block block = block(hopper);

    BukkitGolemLids.apply(block, true);
    BukkitGolemLids.apply(block, false);

    verifyNoInteractions(hopper);
    verify(block, never()).getState();
  }

  private static Block block(BlockState state) {
    Block block = mock(Block.class);
    lenient().when(block.getState(false)).thenReturn(state);
    return block;
  }
}
