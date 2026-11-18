package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.platform.schedule.BukkitScheduler;
import art.arcane.adapt.bukkit.platform.schedule.SchedulerBackend;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitSchedulerTest {
  private SchedulerBackend backend;
  private PlatformLog log;
  private BukkitScheduler scheduler;

  @BeforeEach
  void setUp() {
    backend = mock(SchedulerBackend.class);
    log = mock(PlatformLog.class);
    scheduler = new BukkitScheduler(backend, log);
    when(backend.runGlobal(any(Runnable.class), anyLong())).thenReturn(true);
  }

  @AfterEach
  void clearCaches() {
    BukkitRefs.clear();
  }

  @Test
  void globalTasksDelegateWithTheirDelay() {
    AtomicInteger runs = new AtomicInteger();

    assertThat(scheduler.runGlobal(runs::incrementAndGet, 5L)).isTrue();

    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(backend).runGlobal(task.capture(), eq(5L));
    task.getValue().run();
    assertThat(runs).hasValue(1);
  }

  @Test
  void entityTasksRunOnTheNativeEntityWithRetirement() {
    Zombie zombie = zombie();
    EntityRef ref = BukkitRefs.entity(zombie);
    when(backend.runEntity(same(zombie), any(Runnable.class), eq(2L), any(Runnable.class))).thenReturn(true);
    AtomicInteger retired = new AtomicInteger();

    assertThat(scheduler.runEntity(ref, () -> { }, 2L, retired::incrementAndGet)).isTrue();

    ArgumentCaptor<Runnable> retire = ArgumentCaptor.forClass(Runnable.class);
    verify(backend).runEntity(same(zombie), any(Runnable.class), eq(2L), retire.capture());
    retire.getValue().run();
    assertThat(retired).hasValue(1);
  }

  @Test
  void entityTasksWithoutRetirementPassNull() {
    Zombie zombie = zombie();
    scheduler.runEntity(BukkitRefs.entity(zombie), () -> { });

    verify(backend).runEntity(same(zombie), any(Runnable.class), eq(0L), isNull());
  }

  @Test
  void ownershipChecksUnwrapHandles() {
    Zombie zombie = zombie();
    when(backend.owns(zombie)).thenReturn(true);

    assertThat(scheduler.owns(BukkitRefs.entity(zombie))).isTrue();
    assertThat(scheduler.owns((EntityRef) null)).isFalse();
  }

  @Test
  void cancelAllDropsQueuedOneShotTasks() {
    AtomicInteger runs = new AtomicInteger();
    scheduler.runGlobal(runs::incrementAndGet, 10L);
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(backend).runGlobal(task.capture(), eq(10L));

    scheduler.cancelAll();
    task.getValue().run();

    assertThat(runs).hasValue(0);
  }

  @Test
  void repeatingTasksRearmWithTheirPeriodUntilCancelled() {
    AtomicInteger runs = new AtomicInteger();
    TaskHandle handle = scheduler.repeatGlobal(runs::incrementAndGet, 3L, 7L);

    ArgumentCaptor<Runnable> first = ArgumentCaptor.forClass(Runnable.class);
    verify(backend).runGlobal(first.capture(), eq(3L));
    first.getValue().run();
    assertThat(runs).hasValue(1);

    ArgumentCaptor<Runnable> second = ArgumentCaptor.forClass(Runnable.class);
    verify(backend).runGlobal(second.capture(), eq(7L));
    handle.cancel();
    second.getValue().run();

    assertThat(runs).hasValue(1);
    assertThat(handle.isCancelled()).isTrue();
    assertThat(scheduler.activeRepeating()).isZero();
    verify(backend, times(1)).runGlobal(any(Runnable.class), eq(7L));
  }

  @Test
  void repeatingEntityTaskRetiresOnceAndStops() {
    Zombie zombie = zombie();
    when(backend.runEntity(same(zombie), any(Runnable.class), anyLong(), any(Runnable.class))).thenReturn(true);
    AtomicInteger retired = new AtomicInteger();

    TaskHandle handle = scheduler.repeatEntity(BukkitRefs.entity(zombie), () -> { }, 0L, 20L, retired::incrementAndGet);

    ArgumentCaptor<Runnable> retire = ArgumentCaptor.forClass(Runnable.class);
    verify(backend).runEntity(same(zombie), any(Runnable.class), eq(0L), retire.capture());
    retire.getValue().run();
    retire.getValue().run();

    assertThat(retired).hasValue(1);
    assertThat(handle.isCancelled()).isTrue();
  }

  @Test
  void repeatThatCannotBeScheduledIsCancelled() {
    when(backend.runGlobal(any(Runnable.class), anyLong())).thenReturn(false);

    TaskHandle handle = scheduler.repeatGlobal(() -> { }, 0L, 1L);

    assertThat(handle.isCancelled()).isTrue();
    assertThat(scheduler.activeRepeating()).isZero();
  }

  @Test
  void failingTasksAreLoggedNotThrown() {
    RuntimeException failure = new IllegalStateException("boom");
    scheduler.runGlobal(() -> {
      throw failure;
    });
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(backend).runGlobal(task.capture(), eq(0L));

    task.getValue().run();

    verify(log).error(any(String.class), same(failure));
  }

  @Test
  void asyncTasksDelegate() {
    AtomicInteger runs = new AtomicInteger();
    scheduler.runAsync(runs::incrementAndGet, 4L);

    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(backend).runAsync(task.capture(), eq(4L));
    task.getValue().run();
    assertThat(runs).hasValue(1);
  }

  private static Zombie zombie() {
    Zombie zombie = mock(Zombie.class);
    when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
    when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
    return zombie;
  }
}
