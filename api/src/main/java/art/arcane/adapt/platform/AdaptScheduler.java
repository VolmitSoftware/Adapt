package art.arcane.adapt.platform;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * Folia-shaped scheduler. On Folia, entity tasks run on the entity's region, location tasks on the
 * location's region, global tasks on the global region. On Paper and on modded servers all of them
 * run on the server thread and every ownership check is true while on that thread.
 *
 * <p>Delays and periods are in ticks. A delay of 0 runs on the next tick, or inline when the caller
 * already owns the target and the method says so. {@code run*} methods return false when the task
 * could not be scheduled (entity removed, plugin disabling); the task then never runs. A task that
 * throws is logged and does not reach the caller. The methods may be called from any thread.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface AdaptScheduler {
  /** True on Folia. */
  boolean isRegionized();

  /** True on a thread allowed to touch world state (the server thread, or any region thread on Folia). */
  boolean isTickThread();

  /** True when the current thread may touch the entity: its region thread on Folia, the server thread elsewhere. */
  boolean owns(EntityRef entity);

  /** True when the current thread owns the region that contains {@code location}. */
  boolean owns(Loc location);

  /** True when the current thread owns the region that contains the chunk. */
  boolean owns(WorldRef world, int chunkX, int chunkZ);

  /** True when every chunk within the radius of {@code center} is owned by the current thread. */
  boolean ownsArea(Loc center, double radiusX, double radiusZ);

  /**
   * Runs on the entity's owner. {@code retired} runs instead if the entity is removed before the
   * task fires; it may be null. Without a delay on the owning thread both run immediately.
   */
  boolean runEntity(EntityRef entity, Runnable task, long delayTicks, Runnable retired);

  /** {@link #runEntity(EntityRef, Runnable, long, Runnable)} without a delay or a retired callback. */
  default boolean runEntity(EntityRef entity, Runnable task) {
    return runEntity(entity, task, 0L, null);
  }

  /** {@link #runEntity(EntityRef, Runnable, long, Runnable)} without a retired callback. */
  default boolean runEntity(EntityRef entity, Runnable task, long delayTicks) {
    return runEntity(entity, task, delayTicks, null);
  }

  /** {@link #runEntity(EntityRef, Runnable, long, Runnable)} without a delay. */
  default boolean runEntity(EntityRef entity, Runnable task, Runnable retired) {
    return runEntity(entity, task, 0L, retired);
  }

  /** Runs on the owner of {@code location}'s region after {@code delayTicks}. */
  boolean runAt(Loc location, Runnable task, long delayTicks);

  /** {@link #runAt(Loc, Runnable, long)} on the next tick. */
  default boolean runAt(Loc location, Runnable task) {
    return runAt(location, task, 0L);
  }

  /** Runs on the global region thread on Folia, the server thread elsewhere, after {@code delayTicks}. */
  boolean runGlobal(Runnable task, long delayTicks);

  /** {@link #runGlobal(Runnable, long)} on the next tick. */
  default boolean runGlobal(Runnable task) {
    return runGlobal(task, 0L);
  }

  /**
   * Off-thread work after {@code delayTicks}. Must not touch world, entity or player state. Cancelling the handle
   * before the task starts keeps it from running; the handle is already cancelled when it could not be scheduled.
   */
  TaskHandle runAsync(Runnable task, long delayTicks);

  /** {@link #runAsync(Runnable, long)} without a delay. */
  default TaskHandle runAsync(Runnable task) {
    return runAsync(task, 0L);
  }

  /**
   * Repeats on the entity's owner every {@code periodTicks} (at least 1), starting after {@code delayTicks}. When
   * the entity is removed the task stops and {@code retired} runs if it is not null. The handle is already
   * cancelled when the task could not be scheduled.
   */
  TaskHandle repeatEntity(EntityRef entity, Runnable task, long delayTicks, long periodTicks, Runnable retired);

  /** Repeats on the owner of {@code location}'s region, starting after {@code delayTicks}. */
  TaskHandle repeatAt(Loc location, Runnable task, long delayTicks, long periodTicks);

  /** Repeats on the global region thread on Folia, the server thread elsewhere; the period is at least 1 tick. */
  TaskHandle repeatGlobal(Runnable task, long delayTicks, long periodTicks);

  /** Repeats off-thread on the tick clock; the period is at least 1 tick. Must not touch world, entity or player state. */
  TaskHandle repeatAsync(Runnable task, long delayTicks, long periodTicks);
}
