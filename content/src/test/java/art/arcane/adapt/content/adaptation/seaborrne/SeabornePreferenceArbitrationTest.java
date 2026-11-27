package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SeabornePreferenceArbitrationTest extends AdaptTestBase {
  @Test
  void foodReserveAccountsForPendingExhaustionAndSaturation() {
    assertThat(SeaborneJet.respectsFoodReserve(9, 0F, 0F, 12D, 8)).isFalse();
    assertThat(SeaborneJet.respectsFoodReserve(8, 4F, 0F, 12D, 8)).isTrue();
    assertThat(SeaborneJet.respectsFoodReserve(4, 0F, 3F, 2D, 4)).isFalse();
    assertThat(SeaborneJet.respectsFoodReserve(1, 0F, 0F, 12D, 0)).isTrue();
  }

  @Test
  void sharedSneakHasOneWinnerAndUnavailableTidecallerReleasesIt() {
    SeaborneHydroJet hydro = spy(TestContent.adaptation(Id.adapt("seaborne"), SeaborneHydroJet::new));
    SeaborneTidecaller tide = mock(SeaborneTidecaller.class);
    Skill<?> skill = mock(Skill.class);
    KList<Adaptation<?>> adaptations = new KList<>();
    adaptations.add(hydro);
    adaptations.add(tide);
    when(skill.getAdaptations()).thenReturn(adaptations);
    doReturn(skill).when(hydro).getSkill();
    PlayerRef player = mock(PlayerRef.class);
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
    SeaborneHydroJet hydro = spy(TestContent.adaptation(Id.adapt("seaborne"), SeaborneHydroJet::new));
    PlayerRef player = mock(PlayerRef.class);
    doReturn(false).when(hydro).isPlayerEnabled(player);
    assertThat(hydro.reservesSneak(player)).isFalse();
  }
}
