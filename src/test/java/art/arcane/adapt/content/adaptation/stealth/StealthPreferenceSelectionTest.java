package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class StealthPreferenceSelectionTest extends AdaptTestBase {
  @Test
  void trapFiltersAreIndependent() {
    assertThat(StealthTrapSense.matchesTrap(Material.TRAPPED_CHEST, true, false, false, false)).isTrue();
    assertThat(StealthTrapSense.matchesTrap(Material.SCULK_SENSOR, true, false, false, false)).isFalse();
    assertThat(StealthTrapSense.matchesTrap(Material.OAK_PRESSURE_PLATE, false, false, true, false)).isTrue();
    assertThat(StealthTrapSense.matchesTrap(Material.TRIPWIRE, false, true, false, false)).isTrue();
    assertThat(StealthTrapSense.matchesTrap(Material.STONE, true, true, true, true)).isFalse();
  }

  @Test
  void doubleSneakReservesOnlyAnExistingEnabledDecoySwap() {
    StealthShadowDecoy decoy = mock(StealthShadowDecoy.class);
    StealthDecoySwap swap = spy(new StealthDecoySwap(decoy));
    Player player = mock(Player.class);
    UUID id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    doReturn(true).when(swap).isPlayerEnabled(player);
    doReturn(1).when(swap).getActiveLevel(player);
    doReturn(StealthDecoySwap.Gesture.DOUBLE).when(swap).preference(player, StealthDecoySwap.GESTURE);
    when(decoy.hasActiveDecoy(id)).thenReturn(false);
    assertThat(swap.reservesDoubleSneak(player)).isFalse();
    when(decoy.hasActiveDecoy(id)).thenReturn(true);
    assertThat(swap.reservesDoubleSneak(player)).isTrue();
    doReturn(StealthDecoySwap.Gesture.HAND_SWAP).when(swap).preference(player, StealthDecoySwap.GESTURE);
    assertThat(swap.reservesDoubleSneak(player)).isFalse();
    doReturn(false).when(swap).isPlayerEnabled(player);
    assertThat(swap.reservesDoubleSneak(player)).isFalse();
  }
}
