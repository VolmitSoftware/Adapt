package art.arcane.adapt;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;
import org.mockito.verification.VerificationMode;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * A static mock of {@link Tasks} that also routes {@code AdaptApi.scheduler()} to the matching {@code Tasks}
 * overload, so content scheduling through the public scheduler reaches the same stubs and verifications.
 * Use it wherever a test would call {@code mockStatic(Tasks.class)}.
 */
public final class SchedulerForwarding implements AdaptScheduler {
  private static final SchedulerForwarding INSTANCE = new SchedulerForwarding();

  private SchedulerForwarding() {
  }

  public static MockedStatic<Tasks> mockTasks() {
    return open(mockStatic(Tasks.class));
  }

  public static MockedStatic<Tasks> mockTasks(Answer<Object> defaultAnswer) {
    return open(mockStatic(Tasks.class, defaultAnswer));
  }

  private static MockedStatic<Tasks> open(MockedStatic<Tasks> tasks) {
    MockedStatic<Engine> engine;
    try {
      engine = mockStatic(Engine.class, CALLS_REAL_METHODS);
    } catch (RuntimeException failure) {
      tasks.close();
      throw failure;
    }
    engine.when(Engine::scheduler).thenReturn(INSTANCE);
    return new Forwarded(tasks, engine);
  }

  private static final class Forwarded implements MockedStatic<Tasks> {
    private final MockedStatic<Tasks> tasks;
    private final MockedStatic<Engine> engine;

    private Forwarded(MockedStatic<Tasks> tasks, MockedStatic<Engine> engine) {
      this.tasks = tasks;
      this.engine = engine;
    }

    @Override
    public <S> OngoingStubbing<S> when(Verification verification) {
      return tasks.when(verification);
    }

    @Override
    public void verify(Verification verification) {
      tasks.verify(verification);
    }

    @Override
    public void verify(Verification verification, VerificationMode mode) {
      tasks.verify(verification, mode);
    }

    @Override
    public void reset() {
      tasks.reset();
    }

    @Override
    public void clearInvocations() {
      tasks.clearInvocations();
    }

    @Override
    public void verifyNoMoreInteractions() {
      tasks.verifyNoMoreInteractions();
    }

    @Override
    public void verifyNoInteractions() {
      tasks.verifyNoInteractions();
    }

    @Override
    public boolean isClosed() {
      return tasks.isClosed();
    }

    @Override
    public void close() {
      try {
        tasks.close();
      } finally {
        engine.close();
      }
    }

    @Override
    public void closeOnDemand() {
      try {
        tasks.closeOnDemand();
      } finally {
        engine.closeOnDemand();
      }
    }
  }

  @Override
  public boolean isRegionized() {
    return Tasks.isFoliaThreading();
  }

  @Override
  public boolean isTickThread() {
    return Tasks.isPrimaryThread();
  }

  @Override
  public boolean owns(EntityRef entity) {
    return Tasks.isOwnedByCurrentRegion(entity);
  }

  @Override
  public boolean owns(Loc location) {
    return Tasks.isOwnedByCurrentRegion(location);
  }

  @Override
  public boolean owns(WorldRef world, int chunkX, int chunkZ) {
    return Tasks.isOwnedByCurrentRegion(world, chunkX, chunkZ);
  }

  @Override
  public boolean ownsArea(Loc center, double radiusX, double radiusZ) {
    return Tasks.isOwnedByCurrentRegion(center, radiusX, radiusZ);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, long delayTicks, Runnable retired) {
    if (delayTicks == 0L) {
      return retired == null ? Tasks.runEntity(entity, task) : Tasks.runEntity(entity, task, retired);
    }
    return retired == null ? Tasks.runEntity(entity, task, (int) delayTicks)
        : Tasks.runEntity(entity, task, (int) delayTicks, retired);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task) {
    return runEntity(entity, task, 0L, null);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, long delayTicks) {
    return runEntity(entity, task, delayTicks, null);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, Runnable retired) {
    return runEntity(entity, task, 0L, retired);
  }

  @Override
  public boolean runAt(Loc location, Runnable task, long delayTicks) {
    return delayTicks == 0L ? Tasks.runAt(location, task) : Tasks.runAt(location, task, (int) delayTicks);
  }

  @Override
  public boolean runAt(Loc location, Runnable task) {
    return runAt(location, task, 0L);
  }

  @Override
  public boolean runGlobal(Runnable task, long delayTicks) {
    if (delayTicks == 0L) {
      Tasks.s(task);
    } else {
      Tasks.s(task, (int) delayTicks);
    }
    return task != null;
  }

  @Override
  public boolean runGlobal(Runnable task) {
    return runGlobal(task, 0L);
  }

  @Override
  public TaskHandle runAsync(Runnable task, long delayTicks) {
    if (delayTicks == 0L) {
      Tasks.a(task);
    } else {
      Tasks.a(task, (int) delayTicks);
    }
    return new Handed();
  }

  @Override
  public TaskHandle runAsync(Runnable task) {
    return runAsync(task, 0L);
  }

  @Override
  public TaskHandle repeatEntity(EntityRef entity, Runnable task, long delayTicks, long periodTicks,
                                 Runnable retired) {
    return Tasks.repeatEntity(entity, task, (int) delayTicks, (int) periodTicks, retired);
  }

  @Override
  public TaskHandle repeatAt(Loc location, Runnable task, long delayTicks, long periodTicks) {
    throw new UnsupportedOperationException("Tasks has no location repeat");
  }

  @Override
  public TaskHandle repeatGlobal(Runnable task, long delayTicks, long periodTicks) {
    return Tasks.sr(task, (int) periodTicks);
  }

  @Override
  public TaskHandle repeatAsync(Runnable task, long delayTicks, long periodTicks) {
    return Tasks.ar(task, (int) periodTicks);
  }

  private static final class Handed implements TaskHandle {
    private volatile boolean cancelled;

    @Override
    public void cancel() {
      cancelled = true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }
  }
}
