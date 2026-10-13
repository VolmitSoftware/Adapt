package art.arcane.adapt.api.adaptation;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BonusBlockBreaksTest {
  @Test
  void nativeBreakDispatchesEventsAndOnlyConfiguredDurabilityIsCharged() {
    Player player = mock(Player.class);
    Block block = mock(Block.class);
    PlayerItemDamageEvent damage = mock(PlayerItemDamageEvent.class);
    when(damage.getPlayer()).thenReturn(player);
    when(player.breakBlock(block)).thenAnswer(invocation -> {
      assertThat(BonusBlockBreaks.isActive(player)).isTrue();
      new BonusBlockBreaks().onItemDamage(damage);
      return true;
    });

    assertThat(BonusBlockBreaks.breakBlock(player, block, 3)).isTrue();

    verify(damage).setCancelled(true);
    verify(player).damageItemStack(EquipmentSlot.HAND, 3);
    assertThat(BonusBlockBreaks.isActive(player)).isFalse();
  }

  @Test
  void rejectedAndFreeBreaksDoNotChargeDurability() {
    Player player = mock(Player.class);
    Block block = mock(Block.class);
    when(player.breakBlock(block)).thenReturn(false, true);

    assertThat(BonusBlockBreaks.breakBlock(player, block, 3)).isFalse();
    assertThat(BonusBlockBreaks.breakBlock(player, block, 0)).isTrue();

    verify(player, never()).damageItemStack(any(EquipmentSlot.class), anyInt());
  }

  @Test
  void failedBreakClearsTheRecursionAndDamageSuppressionContext() {
    Player player = mock(Player.class);
    Block block = mock(Block.class);
    when(player.breakBlock(block)).thenThrow(new IllegalStateException("Rejected break"));

    assertThatThrownBy(() -> BonusBlockBreaks.breakBlock(player, block, 1))
        .isInstanceOf(IllegalStateException.class);
    assertThat(BonusBlockBreaks.isActive(player)).isFalse();
  }
}
