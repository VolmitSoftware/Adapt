package art.arcane.adapt.util.common.scheduling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingTaskExecutorTest {
  @Test
  void usesVirtualThreadsWhenTheRuntimeHasThem() throws Exception {
    assertThat(BlockingTaskExecutor.virtualThreadFactory()).isNotNull();
    ExecutorService executor = BlockingTaskExecutor.create("test-claims");
    try {
      Future<Boolean> virtual = executor.submit(() -> Thread.currentThread().isVirtual());
      assertThat(virtual.get(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void platformFallbackRunsBlockingTasksConcurrentlyOnNamedDaemonThreads() throws Exception {
    ExecutorService executor = BlockingTaskExecutor.platformThreads("test-claims");
    int tasks = 16;
    CountDownLatch allStarted = new CountDownLatch(tasks);
    CountDownLatch release = new CountDownLatch(1);
    try {
      for (int index = 0; index < tasks; index++) {
        executor.execute(() -> {
          assertThat(Thread.currentThread().isDaemon()).isTrue();
          assertThat(Thread.currentThread().getName()).startsWith("test-claims-");
          allStarted.countDown();
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
        });
      }
      assertThat(allStarted.await(5, TimeUnit.SECONDS)).as("every blocking task runs at once").isTrue();
    } finally {
      release.countDown();
      executor.shutdown();
    }
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
  }
}
