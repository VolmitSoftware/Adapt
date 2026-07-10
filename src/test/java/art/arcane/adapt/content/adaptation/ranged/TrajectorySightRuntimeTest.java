package art.arcane.adapt.content.adaptation.ranged;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TrajectorySightRuntimeTest {
  @Test
  void productionScaleCapsOneThousandContenders() {
    AtomicLong clock = new AtomicLong();
    TrajectorySightBudget budget = new TrajectorySightBudget(12, 512, 50L, clock::get);
    int renderGrants = 0;
    int rayTraceGrants = 0;

    for (int index = 0; index < 1_000; index++) {
      if (budget.tryAcquireRender(new UUID(0L, index + 1L))) {
        renderGrants++;
      }
      if (budget.tryAcquireRayTrace()) {
        rayTraceGrants++;
      }
    }

    assertThat(renderGrants).isEqualTo(12);
    assertThat(rayTraceGrants).isEqualTo(512);
    assertThat(budget.renderCount()).isEqualTo(12);
    assertThat(budget.rayTraceCount()).isEqualTo(512);
  }

  @Test
  void enforcesRenderLimitAndResetsAtNextWindow() {
    AtomicLong clock = new AtomicLong();
    TrajectorySightBudget budget = new TrajectorySightBudget(3, 5, 50L, clock::get);
    UUID first = new UUID(0L, 1L);
    UUID second = new UUID(0L, 2L);
    UUID third = new UUID(0L, 3L);
    UUID fourth = new UUID(0L, 4L);

    assertThat(budget.tryAcquireRender(first)).isTrue();
    assertThat(budget.tryAcquireRender(second)).isTrue();
    assertThat(budget.tryAcquireRender(third)).isTrue();
    assertThat(budget.tryAcquireRender(fourth)).isFalse();
    assertThat(budget.renderCount()).isEqualTo(3);

    clock.set(50L);
    assertThat(budget.tryAcquireRender(fourth)).isTrue();
    assertThat(budget.renderCount()).isEqualTo(1);
  }

  @Test
  void enforcesRayTraceLimitExactly() {
    AtomicLong clock = new AtomicLong();
    TrajectorySightBudget budget = new TrajectorySightBudget(2, 3, 50L, clock::get);

    assertThat(budget.tryAcquireRayTrace()).isTrue();
    assertThat(budget.tryAcquireRayTrace()).isTrue();
    assertThat(budget.tryAcquireRayTrace()).isTrue();
    assertThat(budget.tryAcquireRayTrace()).isFalse();
    assertThat(budget.rayTraceCount()).isEqualTo(3);

    clock.set(50L);
    assertThat(budget.tryAcquireRayTrace()).isTrue();
    assertThat(budget.rayTraceCount()).isEqualTo(1);
  }

  @Test
  void saturatedRenderersReceiveFifoReservations() {
    AtomicLong clock = new AtomicLong();
    TrajectorySightBudget budget = new TrajectorySightBudget(1, 10, 50L, clock::get);
    UUID first = new UUID(0L, 1L);
    UUID queuedFirst = new UUID(0L, 2L);
    UUID queuedSecond = new UUID(0L, 3L);

    assertThat(budget.tryAcquireRender(first)).isTrue();
    assertThat(budget.tryAcquireRender(queuedFirst)).isFalse();
    assertThat(budget.tryAcquireRender(queuedSecond)).isFalse();
    assertThat(budget.waitingCount()).isEqualTo(2);

    clock.set(50L);
    assertThat(budget.tryAcquireRender(first)).isFalse();
    assertThat(budget.tryAcquireRender(queuedFirst)).isTrue();
    assertThat(budget.tryAcquireRender(queuedSecond)).isFalse();

    clock.set(100L);
    assertThat(budget.tryAcquireRender(queuedSecond)).isTrue();
  }

  @Test
  void stoppingSessionInvalidatesQueuedCallbacks() {
    TrajectorySightSessionGate session = new TrajectorySightSessionGate();
    long token = session.start();

    assertThat(token).isPositive();
    assertThat(session.start()).isEqualTo(-1L);
    assertThat(session.isCurrent(token)).isTrue();

    session.stop();

    assertThat(session.isRunning()).isFalse();
    assertThat(session.isCurrent(token)).isFalse();
    long restarted = session.start();
    assertThat(restarted).isGreaterThan(token);
    assertThat(session.isCurrent(restarted)).isTrue();
  }
}
