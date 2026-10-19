package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerSkillLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TragoulSkeletalServantGolemancyTest {
  @Test
  void theGolemancyBonusesDefaultToTheCrossoverValues() {
    TragoulSkeletalServant.Config config = new TragoulSkeletalServant.Config();

    assertThat(config.golemancyCapBonusEveryLevels).isEqualTo(20);
    assertThat(config.golemancyCapBonusMax).isEqualTo(2);
    assertThat(config.golemancyDurationBonusPerLevel).isEqualTo(0.01D);
    assertThat(config.golemancyDurationBonusMax).isEqualTo(0.5D);
  }

  @ParameterizedTest
  @CsvSource({
      "0, 0",
      "19, 0",
      "20, 1",
      "39, 1",
      "40, 2",
      "100, 2"
  })
  void theServantCapGainsOneEveryTwentyGolemancyLevelsUpToTwo(int golemancyLevel, int bonus) {
    assertThat(TragoulSkeletalServant.golemancyCapBonus(golemancyLevel, 20, 2)).isEqualTo(bonus);
  }

  @Test
  void aNonPositiveStepOrMaximumGivesNoCapBonus() {
    assertThat(TragoulSkeletalServant.golemancyCapBonus(60, 0, 2)).isZero();
    assertThat(TragoulSkeletalServant.golemancyCapBonus(60, 20, 0)).isZero();
    assertThat(TragoulSkeletalServant.golemancyCapBonus(-5, 20, 2)).isZero();
  }

  @Test
  void theGolemancyLevelCountsOnlyWhileTheSkillIsRegisteredAndEnabled() {
    Skill<?> golemancy = mock(Skill.class);
    PlayerSkillLine line = mock(PlayerSkillLine.class);
    when(line.getLevel()).thenReturn(37);

    assertThat(TragoulSkeletalServant.golemancyLevel(null, line)).isZero();
    when(golemancy.isEnabled()).thenReturn(false);
    assertThat(TragoulSkeletalServant.golemancyLevel(golemancy, line)).isZero();
    when(golemancy.isEnabled()).thenReturn(true);
    assertThat(TragoulSkeletalServant.golemancyLevel(golemancy, null)).isZero();
    assertThat(TragoulSkeletalServant.golemancyLevel(golemancy, line)).isEqualTo(37);
  }

  @Test
  void theBonusRaisesTheCapButNeverPastTheHardCeiling() {
    assertThat(TragoulSkeletalServant.servantCap(5, 2)).isEqualTo(7);
    assertThat(TragoulSkeletalServant.servantCap(15, 2)).isEqualTo(16);
    assertThat(TragoulSkeletalServant.servantCap(16, 0)).isEqualTo(16);
  }

  @ParameterizedTest
  @CsvSource({
      "0, 1.0",
      "25, 1.25",
      "50, 1.5",
      "80, 1.5"
  })
  void theDurationGrowsOnePercentPerGolemancyLevelUpToHalfAgain(int golemancyLevel, double multiplier) {
    assertThat(TragoulSkeletalServant.golemancyDurationMultiplier(golemancyLevel, 0.01D, 0.5D)).isCloseTo(multiplier, offset(1e-9));
  }

  @Test
  void brokenDurationBonusSettingsLeaveTheDurationUnchanged() {
    assertThat(TragoulSkeletalServant.golemancyDurationMultiplier(30, Double.NaN, 0.5D)).isEqualTo(1D);
    assertThat(TragoulSkeletalServant.golemancyDurationMultiplier(30, 0.01D, -1D)).isEqualTo(1D);
    assertThat(TragoulSkeletalServant.golemancyDurationMultiplier(-30, 0.01D, 0.5D)).isEqualTo(1D);
  }
}
