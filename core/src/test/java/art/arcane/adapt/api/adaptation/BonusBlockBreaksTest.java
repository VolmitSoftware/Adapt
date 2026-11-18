package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BonusBlockBreaksTest {
  private ProtectionHost protection;
  private ComponentPort components;

  @BeforeEach
  void installCore() {
    AdaptCore core = TestCores.install();
    protection = core.getPlatform().protection();
    components = mock(ComponentPort.class);
    AdaptPorts.install(ComponentPort.class, components);
  }

  @AfterEach
  void clearCore() {
    AdaptPorts.uninstall(ComponentPort.class);
    TestCores.clear();
  }

  @Test
  void nativeBreakDispatchesEventsAndOnlyConfiguredDurabilityIsCharged() {
    PlayerRef player = player();
    BlockRef block = mock(BlockRef.class);
    PlayerItemDamageEvent damage = new PlayerItemDamageEvent(player, mock(ItemRef.class), 1, 1);
    when(protection.attemptBreak(player, block)).thenAnswer(invocation -> {
      assertThat(BonusBlockBreaks.isActive(player)).isTrue();
      new BonusBlockBreaks().onItemDamage(damage);
      return true;
    });

    assertThat(BonusBlockBreaks.breakBlock(player, block, 3)).isTrue();

    assertThat(damage.isCancelled()).isTrue();
    verify(components).damageItem(player, EquipmentSlot.MAIN_HAND, 3);
    assertThat(BonusBlockBreaks.isActive(player)).isFalse();
  }

  @Test
  void rejectedAndFreeBreaksDoNotChargeDurability() {
    PlayerRef player = player();
    BlockRef block = mock(BlockRef.class);
    when(protection.attemptBreak(player, block)).thenReturn(false, true);

    assertThat(BonusBlockBreaks.breakBlock(player, block, 3)).isFalse();
    assertThat(BonusBlockBreaks.breakBlock(player, block, 0)).isTrue();

    verify(components, never()).damageItem(any(PlayerRef.class), any(EquipmentSlot.class), anyInt());
  }

  @Test
  void failedBreakClearsTheRecursionAndDamageSuppressionContext() {
    PlayerRef player = player();
    BlockRef block = mock(BlockRef.class);
    when(protection.attemptBreak(player, block)).thenThrow(new IllegalStateException("Rejected break"));

    assertThatThrownBy(() -> BonusBlockBreaks.breakBlock(player, block, 1))
        .isInstanceOf(IllegalStateException.class);
    assertThat(BonusBlockBreaks.isActive(player)).isFalse();
  }

  private static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    return player;
  }
}
