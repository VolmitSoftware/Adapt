package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SeabornePreferenceArbitrationTest extends AdaptTestBase {
  @Test
  void foodReserveAccountsForPendingExhaustionAndSaturation() {
    assertThat(SeaborneHydroJet.respectsFoodReserve(9, 0F, 0F, 12D, 8)).isFalse();
    assertThat(SeaborneHydroJet.respectsFoodReserve(8, 4F, 0F, 12D, 8)).isTrue();
    assertThat(SeaborneHydroJet.respectsFoodReserve(4, 0F, 3F, 2D, 4)).isFalse();
    assertThat(SeaborneHydroJet.respectsFoodReserve(1, 0F, 0F, 12D, 0)).isTrue();
  }

  @Test
  void sharedSneakHasOneWinnerAndUnavailableTidecallerReleasesIt() {
    SeaborneHydroJet hydro = spy(new SeaborneHydroJet());
    SeaborneTidecaller tide = mock(SeaborneTidecaller.class);
    Skill<?> skill = mock(Skill.class);
    KList<Adaptation<?>> adaptations = new KList<>();
    adaptations.add(hydro);
    adaptations.add(tide);
    when(skill.getAdaptations()).thenReturn(adaptations);
    hydro.setSkill(skill);
    Player player = mock(Player.class);
    when(player.isSwimming()).thenReturn(true);
    doReturn(true).when(hydro).isPlayerEnabled(player);
    doReturn(2).when(hydro).getActiveLevel(player);
    doReturn(SeaborneHydroJet.Priority.HYDRO).when(hydro).preference(player, SeaborneHydroJet.PRIORITY);
    when(tide.usesSneakTrigger(player)).thenReturn(true);
    assertThat(hydro.reservesSneak(player)).isTrue();
    doReturn(SeaborneHydroJet.Priority.TIDE).when(hydro).preference(player, SeaborneHydroJet.PRIORITY);
    assertThat(hydro.reservesSneak(player)).isFalse();
    when(tide.usesSneakTrigger(player)).thenReturn(false);
    assertThat(hydro.reservesSneak(player)).isTrue();
    doReturn(0).when(hydro).getActiveLevel(player);
    assertThat(hydro.reservesSneak(player)).isFalse();
  }

  @Test
  void disablingHydroNeverReservesTidecallersGesture() {
    SeaborneHydroJet hydro = spy(new SeaborneHydroJet());
    Player player = mock(Player.class);
    doReturn(false).when(hydro).isPlayerEnabled(player);
    assertThat(hydro.reservesSneak(player)).isFalse();
  }
}
