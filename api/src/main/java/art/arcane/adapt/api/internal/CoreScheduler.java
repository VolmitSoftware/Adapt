package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * The scheduler {@code AdaptApi.scheduler()} returns. It forwards to the running core's scheduler and does nothing
 * when no core runs: {@code run*} report false, {@code runAsync} and {@code repeat*} return a cancelled handle,
 * ownership checks are false. A null target or task is treated the same way. Negative delays count as 0 and periods
 * below 1 as 1.
 */
@ApiStatus.Internal
final class CoreScheduler implements AdaptScheduler {
  static final CoreScheduler INSTANCE = new CoreScheduler();

  private CoreScheduler() {
  }

  private static AdaptScheduler running() {
    EngineBridge bridge = Engine.current();
    return bridge == null ? null : bridge.platform().scheduler();
  }

  @Override
  public boolean isRegionized() {
    AdaptScheduler scheduler = running();
    return scheduler != null && scheduler.isRegionized();
  }

  @Override
  public boolean isTickThread() {
    AdaptScheduler scheduler = running();
    return scheduler != null && scheduler.isTickThread();
  }

  @Override
  public boolean owns(EntityRef entity) {
    AdaptScheduler scheduler = running();
    return scheduler != null && entity != null && scheduler.owns(entity);
  }

  @Override
  public boolean owns(Loc location) {
    AdaptScheduler scheduler = running();
    return scheduler != null && location != null && scheduler.owns(location);
  }

  @Override
  public boolean owns(WorldRef world, int chunkX, int chunkZ) {
    AdaptScheduler scheduler = running();
    return scheduler != null && world != null && scheduler.owns(world, chunkX, chunkZ);
  }

  @Override
  public boolean ownsArea(Loc center, double radiusX, double radiusZ) {
    AdaptScheduler scheduler = running();
    return scheduler != null
        && center != null
        && Double.isFinite(radiusX)
        && Double.isFinite(radiusZ)
        && radiusX >= 0.0D
        && radiusZ >= 0.0D
        && scheduler.ownsArea(center, radiusX, radiusZ);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, long delayTicks, Runnable retired) {
    AdaptScheduler scheduler = running();
    return scheduler != null && entity != null && task != null
        && scheduler.runEntity(entity, task, delay(delayTicks), retired);
  }

  @Override
  public boolean runAt(Loc location, Runnable task, long delayTicks) {
    AdaptScheduler scheduler = running();
    return scheduler != null && location != null && task != null
        && scheduler.runAt(location, task, delay(delayTicks));
  }

  @Override
  public boolean runGlobal(Runnable task, long delayTicks) {
    AdaptScheduler scheduler = running();
    return scheduler != null && task != null && scheduler.runGlobal(task, delay(delayTicks));
  }

  @Override
  public TaskHandle runAsync(Runnable task, long delayTicks) {
    AdaptScheduler scheduler = running();
    if (scheduler == null || task == null) {
      return NoTask.INSTANCE;
    }
    return handle(scheduler.runAsync(task, delay(delayTicks)));
  }

  @Override
  public TaskHandle repeatEntity(EntityRef entity, Runnable task, long delayTicks, long periodTicks,
                                 Runnable retired) {
    AdaptScheduler scheduler = running();
    if (scheduler == null || entity == null || task == null) {
      return NoTask.INSTANCE;
    }
    return handle(scheduler.repeatEntity(entity, task, delay(delayTicks), period(periodTicks), retired));
  }

  @Override
  public TaskHandle repeatAt(Loc location, Runnable task, long delayTicks, long periodTicks) {
    AdaptScheduler scheduler = running();
    if (scheduler == null || location == null || task == null) {
      return NoTask.INSTANCE;
    }
    return handle(scheduler.repeatAt(location, task, delay(delayTicks), period(periodTicks)));
  }

  @Override
  public TaskHandle repeatGlobal(Runnable task, long delayTicks, long periodTicks) {
    AdaptScheduler scheduler = running();
    if (scheduler == null || task == null) {
      return NoTask.INSTANCE;
    }
    return handle(scheduler.repeatGlobal(task, delay(delayTicks), period(periodTicks)));
  }

  @Override
  public TaskHandle repeatAsync(Runnable task, long delayTicks, long periodTicks) {
    AdaptScheduler scheduler = running();
    if (scheduler == null || task == null) {
      return NoTask.INSTANCE;
    }
    return handle(scheduler.repeatAsync(task, delay(delayTicks), period(periodTicks)));
  }

  private static long delay(long delayTicks) {
    return Math.max(0L, delayTicks);
  }

  private static long period(long periodTicks) {
    return Math.max(1L, periodTicks);
  }

  private static TaskHandle handle(TaskHandle scheduled) {
    return scheduled == null ? NoTask.INSTANCE : scheduled;
  }

  private static final class NoTask implements TaskHandle {
    private static final NoTask INSTANCE = new NoTask();

    @Override
    public void cancel() {
    }

    @Override
    public boolean isCancelled() {
      return true;
    }
  }
}
