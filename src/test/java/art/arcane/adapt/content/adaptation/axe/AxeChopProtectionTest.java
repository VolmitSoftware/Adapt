package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AxeChopProtectionTest {
  @Test
  void chopPropagatesTheNativePlayerBreakResult() throws Exception {
    AxeChop adaptation = mock(AxeChop.class, CALLS_REAL_METHODS);
    Player player = mock(Player.class);
    World world = mock(World.class);
    Block block = mock(Block.class);
    Location location = mock(Location.class);
    when(block.getWorld()).thenReturn(world);
    when(block.getX()).thenReturn(2);
    when(block.getY()).thenReturn(64);
    when(block.getZ()).thenReturn(3);
    when(block.getType()).thenReturn(Material.OAK_LOG);
    when(block.getLocation()).thenReturn(location);
    when(world.getBlockAt(2, 64, 3)).thenReturn(block);
    doReturn(true).when(adaptation).canBlockBreak(player, location);
    when(player.breakBlock(block)).thenReturn(false, true);

    Adapt previous = Adapt.instance;
    Adapt.instance = mock(Adapt.class);
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);

      assertThat(breakStuff(adaptation, block, player)).isFalse();
      assertThat(breakStuff(adaptation, block, player)).isTrue();
    } finally {
      Adapt.instance = previous;
    }

    verify(player, times(2)).breakBlock(block);
    InOrder order = inOrder(block, player);
    order.verify(block).setMetadata(anyString(), any());
    order.verify(player).breakBlock(block);
    order.verify(block).removeMetadata(anyString(), any());
    order.verify(block).setMetadata(anyString(), any());
    order.verify(player).breakBlock(block);
    order.verify(block).removeMetadata(anyString(), any());
  }

  private boolean breakStuff(AxeChop adaptation, Block block, Player player) throws Exception {
    Method method = AxeChop.class.getDeclaredMethod("breakStuff", Block.class, int.class, Player.class);
    method.setAccessible(true);
    return (boolean) method.invoke(adaptation, block, 1, player);
  }
}
