package art.arcane.adapt.content.adaptation.sword;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwordsLungeStrikeReachWindowTest {
  @Test
  void reachWindowNeverReachesPermanentApplyFallback() {
    assertThat(SwordsLungeStrike.reachWindowTicks(0)).isGreaterThanOrEqualTo(5);
    assertThat(SwordsLungeStrike.reachWindowTicks(-1)).isGreaterThanOrEqualTo(5);
    assertThat(SwordsLungeStrike.reachWindowTicks(-500)).isGreaterThanOrEqualTo(5);
  }

  @Test
  void reachWindowRoundsConfiguredTicks() {
    assertThat(SwordsLungeStrike.reachWindowTicks(12)).isEqualTo(12);
    assertThat(SwordsLungeStrike.reachWindowTicks(12.4)).isEqualTo(12);
    assertThat(SwordsLungeStrike.reachWindowTicks(12.5)).isEqualTo(13);
  }

  @Test
  void reachWindowClampsSmallPositiveConfigToMinimum() {
    assertThat(SwordsLungeStrike.reachWindowTicks(1)).isEqualTo(5);
    assertThat(SwordsLungeStrike.reachWindowTicks(4.9)).isEqualTo(5);
  }

  @Test
  void defaultConfigReachWindowIsUsableAsTimedDuration() {
    SwordsLungeStrike.Config config = new SwordsLungeStrike.Config();
    assertThat(SwordsLungeStrike.reachWindowTicks(config.reachWindowTicks)).isEqualTo(12);
  }
}
