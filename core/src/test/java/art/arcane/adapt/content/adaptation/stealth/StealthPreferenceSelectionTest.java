package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
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
    assertThat(StealthTrapSense.matchesTrap(Id.minecraft("trapped_chest"), true, false, false, false)).isTrue();
    assertThat(StealthTrapSense.matchesTrap(Id.minecraft("sculk_sensor"), true, false, false, false)).isFalse();
    assertThat(StealthTrapSense.matchesTrap(Id.minecraft("oak_pressure_plate"), false, false, true, false)).isTrue();
    assertThat(StealthTrapSense.matchesTrap(Id.minecraft("tripwire"), false, true, false, false)).isTrue();
    assertThat(StealthTrapSense.matchesTrap(Id.minecraft("stone"), true, true, true, true)).isFalse();
  }

  @Test
  void doubleSneakReservesOnlyAnExistingEnabledDecoySwap() {
    StealthShadowDecoy decoy = mock(StealthShadowDecoy.class);
    StealthDecoySwap swap = spy(new StealthDecoySwap(decoy));
    PlayerRef player = mock(PlayerRef.class);
    UUID id = UUID.randomUUID();
    when(player.uuid()).thenReturn(id);
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
