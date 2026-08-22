package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class FxDispatchTest extends AdaptTestBase {
  @Test
  void viewerCommandsShareOneOwnerDispatchUntilDrained() {
    Player player = mock(Player.class);
    AtomicInteger emitted = new AtomicInteger();
    AtomicReference<Runnable> drain = new AtomicReference<>();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        drain.set(invocation.getArgument(1));
        return true;
      });

      FxDispatch.ViewerBatch batch = new FxDispatch.ViewerBatch(player);
      assertThat(batch.enqueue(FxDispatch.emission(viewer -> emitted.incrementAndGet(), null))).isTrue();
      assertThat(batch.enqueue(FxDispatch.emission(viewer -> emitted.incrementAndGet(), null))).isTrue();

      scheduling.verify(() -> J.runEntity(same(player), any(Runnable.class)), times(1));
      assertThat(batch.pendingCount()).isEqualTo(2);

      drain.get().run();

      assertThat(emitted.get()).isEqualTo(2);
      assertThat(batch.pendingCount()).isZero();
    }
  }

  @Test
  void pendingViewerCommandsAreBounded() {
    Player player = mock(Player.class);
    AtomicReference<Runnable> drain = new AtomicReference<>();
    AtomicInteger emitted = new AtomicInteger();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        drain.set(invocation.getArgument(1));
        return true;
      });

      FxDispatch.ViewerBatch batch = new FxDispatch.ViewerBatch(player);
      int capacity = FxBudget.PER_VIEWER_EMISSION_CAP * 2;
      for (int i = 0; i < capacity; i++) {
        assertThat(batch.enqueue(FxDispatch.emission(viewer -> emitted.incrementAndGet(), null))).isTrue();
      }
      assertThat(batch.enqueue(FxDispatch.emission(viewer -> emitted.incrementAndGet(), null))).isFalse();

      drain.get().run();

      assertThat(emitted.get()).isEqualTo(capacity);
      assertThat(batch.pendingCount()).isZero();
    }
  }

  @Test
  void repeatedAsyncEmissionFailureIsReportedOnce() {
    Player player = mock(Player.class);
    AtomicReference<Runnable> drain = new AtomicReference<>();
    AtomicInteger failures = new AtomicInteger();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        drain.set(invocation.getArgument(1));
        return true;
      });

      FxDispatch.Emission failed = FxDispatch.emission(viewer -> {
        throw new IllegalStateException("dispatch failed");
      }, error -> failures.incrementAndGet());
      FxDispatch.ViewerBatch batch = new FxDispatch.ViewerBatch(player);
      assertThat(batch.enqueue(failed)).isTrue();
      assertThat(batch.enqueue(failed)).isTrue();

      drain.get().run();

      assertThat(failures.get()).isEqualTo(1);
      assertThat(batch.pendingCount()).isZero();
    }
  }
}
