package art.arcane.adapt.platform;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

/**
 * Folia-shaped scheduler. On Folia, entity tasks run on the entity's region, location tasks on the
 * location's region, global tasks on the global region. On Paper and on modded servers all of them
 * run on the server thread and every ownership check is true while on that thread.
 *
 * <p>Delays and periods are in ticks. A delay of 0 runs on the next tick, or inline when the caller
 * already owns the target and the method says so. {@code run*} methods return false when the task
 * could not be scheduled (entity removed, plugin disabling); the task then never runs.
 */
public interface AdaptScheduler {
  /** True on Folia. */
  boolean isRegionized();

  /** True on a thread allowed to touch world state (the server thread, or any region thread on Folia). */
  boolean isTickThread();

  boolean owns(EntityRef entity);

  boolean owns(Loc location);

  boolean owns(WorldRef world, int chunkX, int chunkZ);

  /** True when every chunk within the radius of {@code center} is owned by the current thread. */
  boolean ownsArea(Loc center, double radiusX, double radiusZ);

  /**
   * Runs on the entity's owner. {@code retired} runs instead if the entity is removed before the
   * task fires; it may be null. Without a delay on the owning thread both run immediately.
   */
  boolean runEntity(EntityRef entity, Runnable task, long delayTicks, Runnable retired);

  default boolean runEntity(EntityRef entity, Runnable task) {
    return runEntity(entity, task, 0L, null);
  }

  default boolean runEntity(EntityRef entity, Runnable task, long delayTicks) {
    return runEntity(entity, task, delayTicks, null);
  }

  boolean runAt(Loc location, Runnable task, long delayTicks);

  default boolean runAt(Loc location, Runnable task) {
    return runAt(location, task, 0L);
  }

  boolean runGlobal(Runnable task, long delayTicks);

  default boolean runGlobal(Runnable task) {
    return runGlobal(task, 0L);
  }

  /** Off-thread work. Must not touch world, entity or player state. */
  void runAsync(Runnable task, long delayTicks);

  default void runAsync(Runnable task) {
    runAsync(task, 0L);
  }

  TaskHandle repeatEntity(EntityRef entity, Runnable task, long delayTicks, long periodTicks, Runnable retired);

  TaskHandle repeatGlobal(Runnable task, long delayTicks, long periodTicks);

  TaskHandle repeatAsync(Runnable task, long delayTicks, long periodTicks);

  /** Cancels every task scheduled through this scheduler. Called on shutdown. */
  void cancelAll();
}
