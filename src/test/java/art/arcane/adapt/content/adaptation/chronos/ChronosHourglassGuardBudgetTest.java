package art.arcane.adapt.content.adaptation.chronos;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ChronosHourglassGuardBudgetTest {
  @Test
  void targetChainsStopAtTheWindowLimitAndResetExactly() {
    AtomicLong clock = new AtomicLong(1_000L);
    ChronosHourglassGuard.HourglassWorkBudget budget =
        new ChronosHourglassGuard.HourglassWorkBudget(128, 50L, clock::get);

    assertThat(budget.reserve(96)).isEqualTo(96);
    assertThat(budget.reserve(96)).isEqualTo(32);
    assertThat(budget.reserve(1)).isZero();
    clock.set(1_049L);
    assertThat(budget.reserve(1)).isZero();
    clock.set(1_050L);
    assertThat(budget.reserve(32)).isEqualTo(32);
  }

  @Test
  void backwardsClockStartsAFreshWindow() {
    AtomicLong clock = new AtomicLong(1_000L);
    ChronosHourglassGuard.HourglassWorkBudget budget =
        new ChronosHourglassGuard.HourglassWorkBudget(1, 50L, clock::get);

    assertThat(budget.reserve(1)).isEqualTo(1);
    assertThat(budget.reserve(1)).isZero();
    clock.set(900L);
    assertThat(budget.reserve(1)).isEqualTo(1);
  }
}
