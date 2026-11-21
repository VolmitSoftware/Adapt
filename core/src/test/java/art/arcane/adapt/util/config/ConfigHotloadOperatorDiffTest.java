package art.arcane.adapt.util.config;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.AdaptScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConfigHotloadOperatorDiffTest extends AdaptTestBase {
  private static final String BEFORE = "[a]\nkey = \"old\"\n";
  private static final String AFTER = "[a]\nkey = \"new\"\n";

  private final List<String> globalCallers = new CopyOnWriteArrayList<>();
  private final CountDownLatch sent = new CountDownLatch(1);
  private AdaptScheduler scheduler;

  @BeforeEach
  void recordGlobalTasks() {
    scheduler = core.getPlatform().scheduler();
    doAnswer(call -> {
      globalCallers.add(Thread.currentThread().getName());
      sent.countDown();
      return true;
    }).when(scheduler).runGlobal(any(Runnable.class), anyLong());
  }

  @Test
  void operatorDiffIsParsedOnItsOwnWorkerAndSentFromTheServerThread() throws Exception {
    ConfigHotload hotload = started();
    try {
      notifyOps(hotload);

      assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      hotload.stop();
    }
    assertThat(globalCallers).containsExactly("Adapt-Config-Hotload-Diff");
    verify(scheduler, never()).runAsync(any(Runnable.class), anyLong());
  }

  @Test
  void stopDropsAQueuedOperatorDiffAndLaterDiffsBeforeThePluginUnloads() throws Exception {
    ConfigHotload hotload = started();
    CountDownLatch busy = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    try {
      worker(hotload).execute(() -> {
        busy.countDown();
        try {
          release.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      });
      assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();

      notifyOps(hotload);
      hotload.stop();
      notifyOps(hotload);
    } finally {
      release.countDown();
      hotload.stop();
    }
    assertThat(sent.await(200, TimeUnit.MILLISECONDS)).isFalse();
    verify(scheduler, never()).runAsync(any(Runnable.class), anyLong());
  }

  @Test
  void aSlowOperatorDiffDoesNotHoldTheConfigFilePoll() throws Exception {
    ConfigHotload hotload = started();
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch polled = new CountDownLatch(1);
    try {
      worker(hotload).execute(() -> {
        try {
          release.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      });
      notifyOps(hotload);
      executor(hotload, "hotloadIo").execute(polled::countDown);

      assertThat(polled.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      release.countDown();
      hotload.stop();
    }
  }

  private static ConfigHotload started() {
    ConfigHotload hotload = new ConfigHotload();
    hotload.start();
    return hotload;
  }

  private void notifyOps(ConfigHotload hotload) throws Exception {
    Method notify = ConfigHotload.class.getDeclaredMethod("notifyOps", File.class, String.class, String.class);
    notify.setAccessible(true);
    notify.invoke(hotload, new File(dataFolder, "languages/en_US.toml"), BEFORE, AFTER);
  }

  private static ExecutorService worker(ConfigHotload hotload) throws ReflectiveOperationException {
    return executor(hotload, "operatorDiffs");
  }

  private static ExecutorService executor(ConfigHotload hotload, String name) throws ReflectiveOperationException {
    Field field = ConfigHotload.class.getDeclaredField(name);
    field.setAccessible(true);
    return (ExecutorService) field.get(hotload);
  }
}
