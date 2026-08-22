package art.arcane.adapt.content.adaptation.discovery;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryBetterMendingTest {
  @Test
  void spendsCurrentExperiencePointsAndUpdatesLevelProgress() {
    Player player = mock(Player.class);
    when(player.calculateTotalExperiencePoints()).thenReturn(25);

    assertThat(DiscoveryBetterMending.spendExperiencePoints(player, 7)).isTrue();
    verify(player).setExperienceLevelAndProgress(18);
  }

  @Test
  void refusesMissingOrInvalidExperienceCost() {
    Player player = mock(Player.class);
    when(player.calculateTotalExperiencePoints()).thenReturn(6);

    assertThat(DiscoveryBetterMending.spendExperiencePoints(player, 7)).isFalse();
    assertThat(DiscoveryBetterMending.spendExperiencePoints(player, 0)).isFalse();
    assertThat(DiscoveryBetterMending.spendExperiencePoints(null, 1)).isFalse();
    verify(player, never()).setExperienceLevelAndProgress(anyInt());
  }

  @Test
  void restoredDurabilityNeverOvercountsRepairPastFull() {
    assertThat(DiscoveryBetterMending.restoredDurability(5, 20)).isEqualTo(5);
    assertThat(DiscoveryBetterMending.restoredDurability(20, 5)).isEqualTo(5);
    assertThat(DiscoveryBetterMending.restoredDurability(-1, 5)).isZero();
    assertThat(DiscoveryBetterMending.restoredDurability(5, -1)).isZero();
  }
}
