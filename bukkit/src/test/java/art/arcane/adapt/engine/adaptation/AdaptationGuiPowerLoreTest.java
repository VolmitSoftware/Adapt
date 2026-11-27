package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.volmlib.util.format.ColorFormatter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A chest menu level entry names power only where it changes: the slot learning the first level takes, and the slot
 * unlearning the last paid level frees. Learned levels and later levels say nothing about power, never a cost of 0.
 */
class AdaptationGuiPowerLoreTest extends AdaptTestBase {
  @Test
  void onlyTheFirstLevelAndTheLastUnlearnNamePower() {
    Adaptation<?> adaptation = mock(Adaptation.class, Mockito.CALLS_REAL_METHODS);
    when(adaptation.isPermanent()).thenReturn(false);
    AdaptPlayer player = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    when(player.getData()).thenReturn(data);
    when(data.hasPowerAvailable(1)).thenReturn(true);

    assertThat(lore(adaptation, player, 0, 1, 0)).contains("1").contains("Power");
    assertThat(lore(adaptation, player, 0, 3, 0)).contains("1");
    assertThat(lore(adaptation, player, 2, 3, 0)).isEmpty();
    assertThat(lore(adaptation, player, 3, 3, 0)).isEmpty();
    assertThat(lore(adaptation, player, 3, 2, 0)).isEmpty();
    assertThat(lore(adaptation, player, 3, 1, 0)).contains("1").contains("Frees");
    assertThat(lore(adaptation, player, 2, 2, 1)).contains("Frees");
    assertThat(lore(adaptation, player, 1, 1, 1)).isEmpty();
    assertThat(lore(adaptation, player, 1, 2, 1)).contains("1");
    for (int current = 0; current <= 5; current++) {
      for (int target = 1; target <= 5; target++) {
        assertThat(lore(adaptation, player, current, target, 0)).doesNotContain("0 Power");
      }
    }

    when(adaptation.isPermanent()).thenReturn(true);
    assertThat(lore(adaptation, player, 1, 1, 0)).isEmpty();
  }

  @Test
  void aSlotThePlayerCannotAffordSaysSo() {
    Adaptation<?> adaptation = mock(Adaptation.class, Mockito.CALLS_REAL_METHODS);
    AdaptPlayer player = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    when(player.getData()).thenReturn(data);
    when(data.hasPowerAvailable(1)).thenReturn(false);
    assertThat(lore(adaptation, player, 0, 1, 0)).isNotEmpty().doesNotContain("Power Drain");
  }

  private static String lore(Adaptation<?> adaptation, AdaptPlayer player, int current, int target, int kept) {
    int cost = adaptation.getPowerCostFor(target - kept, current - kept);
    return ColorFormatter.stripColor(AdaptationGuiSupport.powerLore(adaptation, player, current, target, Math.max(0, cost), kept));
  }
}
