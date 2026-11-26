package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.AdaptConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UpgradeXpTest {
  @Test
  void anUpgradePaysAtMostItsAllowanceInAMinuteAndRefillsOverTime() {
    double perMinute = AdaptConfig.get().getMutations().getUpgradeXpPerMinute();
    UpgradeXp allowance = new UpgradeXp();
    long now = 1_000_000L;

    double first = allowance.claim(perMinute * 0.75D, now);
    double second = allowance.claim(perMinute * 0.75D, now);
    double spammed = allowance.claim(50D, now + 1L);
    double later = allowance.claim(perMinute, now + 30_001L);

    assertThat(first).isCloseTo(perMinute * 0.75D, within(1.0E-9D));
    assertThat(second).isCloseTo(perMinute * 0.25D, within(1.0E-9D));
    assertThat(spammed).isCloseTo(perMinute / 60_000D, within(1.0E-6D));
    assertThat(later).isCloseTo(perMinute * 0.5D, within(1.0E-3D));
  }
}
