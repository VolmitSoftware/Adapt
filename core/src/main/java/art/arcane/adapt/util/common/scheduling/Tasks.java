package art.arcane.adapt.util.common.scheduling;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.function.Supplier;

/**
 * Static scheduling facade for platform-neutral code, shaped like the Bukkit {@code J} helper:
 * {@code J.runEntity} and {@code Tasks.runEntity} take the same arguments. Every call goes through
 * {@link AdaptScheduler} of the running {@link AdaptCore}. Calls made before the core starts or after it stops do
 * nothing and report {@code false}.
 *
 * <p>Inline-versus-deferred behaviour is the scheduler's: the Bukkit scheduler runs on {@code J}, so a delay-free
 * entity task runs inline when the caller owns the entity.
 */
public final class Tasks {
  private Tasks() {
  }

  public static AdaptScheduler scheduler() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getPlatform().scheduler();
  }

  public static boolean isFoliaThreading() {
    AdaptScheduler scheduler = scheduler();
    return scheduler != null && scheduler.isRegionized();
  }

  public static boolean isPrimaryThread() {
    AdaptScheduler scheduler = scheduler();
    return scheduler != null && scheduler.isTickThread();
  }

  public static boolean isOwnedByCurrentRegion(EntityRef entity) {
    AdaptScheduler scheduler = scheduler();
    return scheduler != null && entity != null && scheduler.owns(entity);
  }

  public static boolean isOwnedByCurrentRegion(Loc location) {
    AdaptScheduler scheduler = scheduler();
    return scheduler != null && location != null && scheduler.owns(location);
  }

  public static boolean isOwnedByCurrentRegion(WorldRef world, int chunkX, int chunkZ) {
    AdaptScheduler scheduler = scheduler();
    return scheduler != null && world != null && scheduler.owns(world, chunkX, chunkZ);
  }

  public static boolean isOwnedByCurrentRegion(Loc center, double radiusX, double radiusZ) {
    AdaptScheduler scheduler = scheduler();
    return scheduler != null
        && center != null
        && Double.isFinite(radiusX)
        && Double.isFinite(radiusZ)
        && radiusX >= 0.0D
        && radiusZ >= 0.0D
        && scheduler.ownsArea(center, radiusX, radiusZ);
  }

  public static boolean runEntity(EntityRef entity, Runnable task) {
    return runEntity(entity, task, 0, null);
  }

  public static boolean runEntity(EntityRef entity, Runnable task, Runnable retired) {
    return runEntity(entity, task, 0, retired);
  }

  public static boolean runEntity(EntityRef entity, Runnable task, int delayTicks) {
    return runEntity(entity, task, delayTicks, null);
  }

  public static boolean runEntity(EntityRef entity, Runnable task, int delayTicks, Runnable retired) {
    AdaptScheduler scheduler = scheduler();
    if (scheduler == null || entity == null || task == null) {
      return false;
    }
    return scheduler.runEntity(entity, task, Math.max(0, delayTicks), retired);
  }

  public static boolean runAt(Loc location, Runnable task) {
    return runAt(location, task, 0);
  }

  public static boolean runAt(Loc location, Runnable task, int delayTicks) {
    AdaptScheduler scheduler = scheduler();
    if (scheduler == null || location == null || task == null) {
      return false;
    }
    return scheduler.runAt(location, task, Math.max(0, delayTicks));
  }

  /** Global-region (server thread) task. */
  public static void s(Runnable task) {
    s(task, 0);
  }

  public static void s(Runnable task, int delayTicks) {
    AdaptScheduler scheduler = scheduler();
    if (scheduler == null || task == null) {
      return;
    }
    scheduler.runGlobal(task, Math.max(0, delayTicks));
  }

  /** Repeating global task every {@code intervalTicks}; cancel it through the returned handle. */
  public static TaskHandle sr(Runnable task, int intervalTicks) {
    AdaptScheduler scheduler = scheduler();
    if (scheduler == null || task == null) {
      return null;
    }
    long period = Math.max(1, intervalTicks);
    return scheduler.repeatGlobal(task, period, period);
  }

  public static void a(Runnable task) {
    a(task, 0);
  }

  public static void a(Runnable task, int delayTicks) {
    AdaptScheduler scheduler = scheduler();
    if (scheduler == null || task == null) {
      return;
    }
    scheduler.runAsync(task, Math.max(0, delayTicks));
  }

  public static TaskHandle ar(Runnable task, int intervalTicks) {
    AdaptScheduler scheduler = scheduler();
    if (scheduler == null || task == null) {
      return null;
    }
    long period = Math.max(1, intervalTicks);
    return scheduler.repeatAsync(task, period, period);
  }

  public static TaskHandle repeatEntity(EntityRef entity, Runnable task, int delayTicks, int intervalTicks,
                                        Runnable retired) {
    AdaptScheduler scheduler = scheduler();
    if (scheduler == null || entity == null || task == null) {
      return null;
    }
    return scheduler.repeatEntity(entity, task, Math.max(0, delayTicks), Math.max(1, intervalTicks), retired);
  }

  public static boolean attempt(ThrowingRunnable task) {
    try {
      task.run();
      return true;
    } catch (Throwable failure) {
      AdaptLog.verbose(() -> "Task attempt failed: " + failure.getMessage());
      return false;
    }
  }

  public static <T> T attempt(Supplier<T> supplier, T fallback) {
    try {
      return supplier.get();
    } catch (Throwable failure) {
      return fallback;
    }
  }

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Throwable;
  }
}
