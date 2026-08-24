package art.arcane.adapt.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandAdaptXpBoostTest {
  @Test
  void acceptsOnlyFiniteBoundedBoostsWithPositiveDurations() {
    assertThat(CommandAdapt.isValidXpBoost(1L, -0.99D)).isTrue();
    assertThat(CommandAdapt.isValidXpBoost(CommandAdapt.MAXIMUM_XP_BOOST_SECONDS, 999D)).isTrue();
    assertThat(CommandAdapt.isValidXpBoost(0L, 1D)).isFalse();
    assertThat(CommandAdapt.isValidXpBoost(-1L, 1D)).isFalse();
    assertThat(CommandAdapt.isValidXpBoost(CommandAdapt.MAXIMUM_XP_BOOST_SECONDS + 1L, 1D)).isFalse();
    assertThat(CommandAdapt.isValidXpBoost(10L, -0.991D)).isFalse();
    assertThat(CommandAdapt.isValidXpBoost(10L, 999.001D)).isFalse();
    assertThat(CommandAdapt.isValidXpBoost(10L, Double.NaN)).isFalse();
    assertThat(CommandAdapt.isValidXpBoost(10L, Double.POSITIVE_INFINITY)).isFalse();
    assertThat(CommandAdapt.isValidXpBoost(10L, Double.NEGATIVE_INFINITY)).isFalse();
  }

  @Test
  void durationConversionRetainsValuesBeyondIntegerMilliseconds() {
    long seconds = (Integer.MAX_VALUE / 1000L) + 10L;

    assertThat(CommandAdapt.xpBoostDurationMillis(seconds)).isEqualTo(seconds * 1000L);
    assertThat(CommandAdapt.xpBoostDurationMillis(CommandAdapt.MAXIMUM_XP_BOOST_SECONDS))
        .isEqualTo(CommandAdapt.MAXIMUM_XP_BOOST_SECONDS * 1000L);
  }
}
