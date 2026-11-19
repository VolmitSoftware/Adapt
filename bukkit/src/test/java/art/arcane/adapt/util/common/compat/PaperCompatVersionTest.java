package art.arcane.adapt.util.common.compat;

import io.papermc.paper.world.WeatheringCopperState;
import net.kyori.adventure.text.Component;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaperCompatVersionTest {
  @Test
  void weatheringStagesCountOxidationStepsOfCopperGolemsOnly() {
    CopperGolem golem = mock(CopperGolem.class);

    for (WeatheringCopperState state : WeatheringCopperState.values()) {
      when(golem.getWeatheringState()).thenReturn(state);
      assertThat(PaperCompat.weatheringStage(golem)).as("stage of %s", state).isEqualTo(switch (state) {
        case UNAFFECTED -> 0;
        case EXPOSED -> 1;
        case WEATHERED -> 2;
        case OXIDIZED -> 3;
      });
    }
    assertThat(PaperCompat.weatheringStage(mock(IronGolem.class))).isZero();
  }

  @Test
  void copperWaxReadsTheOxidizingStateOfCopperGolemsOnly() {
    CopperGolem waxed = mock(CopperGolem.class);
    when(waxed.getOxidizing()).thenReturn(CopperGolem.Oxidizing.waxed());
    CopperGolem oxidizing = mock(CopperGolem.class);
    when(oxidizing.getOxidizing()).thenReturn(CopperGolem.Oxidizing.atTime(40L));

    assertThat(PaperCompat.copperWaxed(waxed)).isTrue();
    assertThat(PaperCompat.copperWaxed(oxidizing)).isFalse();
    assertThat(PaperCompat.copperWaxed(mock(IronGolem.class))).isFalse();
  }

  @Test
  void restoringCopperSetsTheStageAndOnlyWaxesWhenAsked() {
    CopperGolem waxed = mock(CopperGolem.class);
    CopperGolem bare = mock(CopperGolem.class);
    CopperGolem clamped = mock(CopperGolem.class);
    IronGolem iron = mock(IronGolem.class);

    PaperCompat.restoreCopper(waxed, 2, true);
    PaperCompat.restoreCopper(bare, 1, false);
    PaperCompat.restoreCopper(clamped, 9, false);
    PaperCompat.restoreCopper(iron, 3, true);

    verify(waxed).setWeatheringState(WeatheringCopperState.WEATHERED);
    verify(waxed).setOxidizing(CopperGolem.Oxidizing.waxed());
    verify(bare).setWeatheringState(WeatheringCopperState.EXPOSED);
    verify(bare, times(0)).setOxidizing(any());
    verify(clamped).setWeatheringState(WeatheringCopperState.OXIDIZED);
    verifyNoInteractions(iron);
  }

  @Test
  void berriesAndCustomNamesReadThroughThePaperApi() {
    CaveVinesPlant ripe = mock(CaveVinesPlant.class);
    CaveVinesPlant bare = mock(CaveVinesPlant.class);
    when(ripe.hasBerries()).thenReturn(true);
    Zombie named = mock(Zombie.class);
    Zombie nameless = mock(Zombie.class);
    when(named.customName()).thenReturn(Component.text("Bob"));

    assertThat(PaperCompat.hasBerries(ripe)).isTrue();
    assertThat(PaperCompat.hasBerries(bare)).isFalse();
    assertThat(PaperCompat.hasCustomName(named)).isTrue();
    assertThat(PaperCompat.hasCustomName(nameless)).isFalse();
  }
}
