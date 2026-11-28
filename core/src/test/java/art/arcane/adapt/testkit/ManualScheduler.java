package art.arcane.adapt.testkit;

import art.arcane.adapt.api.internal.HostScheduler;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduler driven by {@link #advance(int)} on the thread that created it (the tick thread). Zero-delay tasks
 * requested on the tick thread run inline like an owned entity task on Bukkit; everything else, including work
 * handed over from other threads, waits for the tick it is due. Not regionized unless {@link #regionized(boolean)}.
 */
public final class ManualScheduler implements HostScheduler {
  private final List<Scheduled> queue = new ArrayList<>();
  private final Thread tickThread = Thread.currentThread();
  private long tick;
  private long sequence;
  private final Map<UUID, List<Long>> entityTaskDelays = new HashMap<>();
  private final List<Loc> regionTaskLocations = new ArrayList<>();
  private boolean rejectEntityTasks;
  private volatile boolean regionized;

  /** Makes every later entity task fail to schedule, as Folia does for an entity that left its region. */
  public synchronized void setRejectEntityTasks(boolean reject) {
    this.rejectEntityTasks = reject;
  }

  /** Delays of every entity task requested for {@code entity}, in request order, accepted or not. */
  public synchronized List<Long> entityTaskDelays(EntityRef entity) {
    return List.copyOf(entityTaskDelays.getOrDefault(entity.uuid(), List.of()));
  }

  /** Locations of every region task requested so far, in request order. */
  public synchronized List<Loc> regionTaskLocations() {
    return List.copyOf(regionTaskLocations);
  }

  public synchronized long tick() {
    return tick;
  }

  /** Runs every task due within the next {@code ticks} ticks, in due order. Call it from the tick thread. */
  public void advance(int ticks) {
    long target;
    synchronized (this) {
      target = tick + ticks;
    }
    while (true) {
      Scheduled next;
      synchronized (this) {
        next = nextDue(target);
        if (next == null) {
          break;
        }
        tick = Math.max(tick, next.due);
        queue.remove(next);
        if (next.handle.cancelled) {
          continue;
        }
      }
      next.task.run();
      synchronized (this) {
        if (next.period > 0 && !next.handle.cancelled) {
          queue.add(new Scheduled(next.task, tick + next.period, next.period, sequence++, next.handle));
        }
      }
    }
    synchronized (this) {
      tick = target;
    }
  }

  /** Tasks still waiting. */
  public synchronized int pending() {
    int pending = 0;
    for (Scheduled scheduled : queue) {
      if (!scheduled.handle.cancelled) {
        pending++;
      }
    }
    return pending;
  }

  private Scheduled nextDue(long target) {
    Scheduled best = null;
    for (Scheduled scheduled : queue) {
      if (scheduled.due <= target && (best == null || ORDER.compare(scheduled, best) < 0)) {
        best = scheduled;
      }
    }
    return best;
  }

  private static final Comparator<Scheduled> ORDER = Comparator.comparingLong((Scheduled scheduled) -> scheduled.due)
      .thenComparingLong((Scheduled scheduled) -> scheduled.sequence);

  private synchronized Handle schedule(Runnable task, long delayTicks, long periodTicks) {
    Handle handle = new Handle();
    queue.add(new Scheduled(task, tick + Math.max(1L, delayTicks), periodTicks, sequence++, handle));
    return handle;
  }

  private boolean runOrSchedule(Runnable task, long delayTicks) {
    if (delayTicks <= 0L && isTickThread()) {
      task.run();
    } else {
      schedule(task, delayTicks, 0L);
    }
    return true;
  }

  @Override
  public boolean isRegionized() {
    return regionized;
  }

  /** Reports a regionized server so code paths that only run under region threading can be exercised. */
  public void regionized(boolean value) {
    regionized = value;
  }

  @Override
  public boolean isTickThread() {
    return Thread.currentThread() == tickThread;
  }

  @Override
  public boolean owns(EntityRef entity) {
    return isTickThread();
  }

  @Override
  public boolean owns(Loc location) {
    return isTickThread();
  }

  @Override
  public boolean owns(WorldRef world, int chunkX, int chunkZ) {
    return isTickThread();
  }

  @Override
  public boolean ownsArea(Loc center, double radiusX, double radiusZ) {
    return isTickThread();
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, long delayTicks, Runnable retired) {
    boolean reject;
    synchronized (this) {
      entityTaskDelays.computeIfAbsent(entity.uuid(), (UUID id) -> new ArrayList<>()).add(delayTicks);
      reject = rejectEntityTasks;
    }
    if (reject || !entity.isValid()) {
      if (retired != null) {
        retired.run();
      }
      return false;
    }
    return runOrSchedule(task, delayTicks);
  }

  @Override
  public boolean runAt(Loc location, Runnable task, long delayTicks) {
    synchronized (this) {
      regionTaskLocations.add(location);
    }
    return runOrSchedule(task, delayTicks);
  }

  @Override
  public boolean runGlobal(Runnable task, long delayTicks) {
    return runOrSchedule(task, delayTicks);
  }

  @Override
  public TaskHandle runAsync(Runnable task, long delayTicks) {
    return schedule(task, delayTicks, 0L);
  }

  /** A zero delay on the tick thread fires the first run inline, before the handle is returned, as Bukkit does. */
  @Override
  public TaskHandle repeatEntity(EntityRef entity, Runnable task, long delayTicks, long periodTicks, Runnable retired) {
    long period = Math.max(1L, periodTicks);
    if (delayTicks <= 0L && isTickThread()) {
      task.run();
      return schedule(task, period, period);
    }
    return schedule(task, delayTicks, period);
  }

  @Override
  public TaskHandle repeatAt(Loc location, Runnable task, long delayTicks, long periodTicks) {
    synchronized (this) {
      regionTaskLocations.add(location);
    }
    return schedule(task, delayTicks, Math.max(1L, periodTicks));
  }

  @Override
  public TaskHandle repeatGlobal(Runnable task, long delayTicks, long periodTicks) {
    return schedule(task, delayTicks, Math.max(1L, periodTicks));
  }

  @Override
  public TaskHandle repeatAsync(Runnable task, long delayTicks, long periodTicks) {
    return schedule(task, delayTicks, Math.max(1L, periodTicks));
  }

  @Override
  public synchronized void cancelAll() {
    for (Scheduled scheduled : queue) {
      scheduled.handle.cancel();
    }
    queue.clear();
  }

  private record Scheduled(Runnable task, long due, long period, long sequence, Handle handle) {
  }

  private static final class Handle implements TaskHandle {
    private boolean cancelled;

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
