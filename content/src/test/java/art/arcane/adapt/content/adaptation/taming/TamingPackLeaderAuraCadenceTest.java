package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TamingPackLeaderAuraCadenceTest {
  @Test
  void productionWorkLimitsRemainHardAtOneThousandContenders() {
    assertThat(PackLeaderWorkLimits.ownerRefreshes(1_000)).isEqualTo(16);
    assertThat(PackLeaderWorkLimits.tameableRefreshes(1_000)).isEqualTo(48);
  }

  @Test
  void firstSuccessfulRefreshPreservesOriginalQuarterSecondCredit() {
    assertThat(PackLeaderCadence.elapsedTicks(null, 10_000L, 250L, 4_000L)).isEqualTo(5D);
  }

  @Test
  void batchedRefreshCreditsElapsedActiveTime() {
    assertThat(PackLeaderCadence.elapsedTicks(10_000L, 11_250L, 250L, 4_000L)).isEqualTo(25D);
  }

  @Test
  void delayedCallbacksCannotCreditPastEffectCoverage() {
    assertThat(PackLeaderCadence.elapsedTicks(10_000L, 30_000L, 250L, 4_000L)).isEqualTo(80D);
  }
}
