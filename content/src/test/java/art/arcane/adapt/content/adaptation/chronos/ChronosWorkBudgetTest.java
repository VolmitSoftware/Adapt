package art.arcane.adapt.content.adaptation.chronos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChronosWorkBudgetTest {
  @Test
  void batchesRotateWithoutExceedingTheHardLimit() {
    ChronosWorkBudget.Batch first = ChronosWorkBudget.batch(1000, 0, 900, 512);
    ChronosWorkBudget.Batch second = ChronosWorkBudget.batch(1000, first.nextCursor(), 900, 512);

    assertThat(first).isEqualTo(new ChronosWorkBudget.Batch(0, 512, 512));
    assertThat(second).isEqualTo(new ChronosWorkBudget.Batch(512, 512, 24));
  }

  @Test
  void pulseClockPreservesElapsedWorkAndCapsRunawayCatchUp() {
    ChronosWorkBudget.Pulse initial = ChronosWorkBudget.pulse(1_000L, null, 250L, 4);
    ChronosWorkBudget.Pulse caughtUp = ChronosWorkBudget.pulse(1_750L, initial.nextAt(), 250L, 4);
    ChronosWorkBudget.Pulse capped = ChronosWorkBudget.pulse(10_000L, caughtUp.nextAt(), 250L, 4);

    assertThat(initial).isEqualTo(new ChronosWorkBudget.Pulse(1, 1_250L));
    assertThat(caughtUp).isEqualTo(new ChronosWorkBudget.Pulse(3, 2_000L));
    assertThat(capped).isEqualTo(new ChronosWorkBudget.Pulse(4, 10_250L));
  }

  @Test
  void allocationsRespectGlobalAndPerJobBudgets() {
    ChronosWorkBudget.Allocation allocation = ChronosWorkBudget.allocate(32, 500, 20, 7);
    int total = 0;
    int minimum = Integer.MAX_VALUE;
    int maximum = Integer.MIN_VALUE;
    for (int i = 0; i < allocation.jobCount(); i++) {
      int grant = allocation.grant(i);
      total += grant;
      minimum = Math.min(minimum, grant);
      maximum = Math.max(maximum, grant);
    }

    assertThat(total).isEqualTo(500);
    assertThat(maximum).isLessThanOrEqualTo(20);
    assertThat(maximum - minimum).isLessThanOrEqualTo(1);
  }

  @Test
  void oldestFieldSelectionIsDeterministic() {
    assertThat(ChronosWorkBudget.oldestIndex(List.of(900L, 400L, 700L))).isEqualTo(1);
    assertThat(ChronosWorkBudget.oldestIndex(List.of())).isEqualTo(-1);
  }

  @Test
  void scanIntervalsStayInsideTheSupportedWindow() {
    assertThat(ChronosWorkBudget.bounded(50L, 200L, 250L)).isEqualTo(200L);
    assertThat(ChronosWorkBudget.bounded(225L, 200L, 250L)).isEqualTo(225L);
    assertThat(ChronosWorkBudget.bounded(500L, 200L, 250L)).isEqualTo(250L);
  }
}
