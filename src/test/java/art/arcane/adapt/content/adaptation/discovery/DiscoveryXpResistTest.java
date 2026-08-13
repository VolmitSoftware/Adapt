package art.arcane.adapt.content.adaptation.discovery;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryXpResistTest {
  @Test
  void spendsVanillaLevelsWhenResistanceTriggers() {
    Player player = mock(Player.class);
    when(player.getLevel()).thenReturn(5);

    assertThat(DiscoveryXpResist.spendLevels(player, 3)).isTrue();
    verify(player).giveExpLevels(-3);
  }

  @Test
  void refusesMissingOrInvalidLevelCost() {
    Player player = mock(Player.class);
    when(player.getLevel()).thenReturn(2);

    assertThat(DiscoveryXpResist.spendLevels(player, 3)).isFalse();
    assertThat(DiscoveryXpResist.spendLevels(player, 0)).isFalse();
    assertThat(DiscoveryXpResist.spendLevels(null, 1)).isFalse();
    verify(player, never()).giveExpLevels(anyInt());
  }
}
