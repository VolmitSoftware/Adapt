package art.arcane.adapt.bukkit.platform.schedule;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.api.internal.HostScheduler;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Folia-shaped scheduler over a {@link SchedulerBackend}. Repeating tasks re-arm themselves through the
 * backend after each run, so entity repeats stay on the entity's region. {@link #cancelAll} stops every
 * task this scheduler started, including one-shot tasks still waiting, without touching other plugin tasks.
 */
public final class BukkitScheduler implements HostScheduler {
  private final SchedulerBackend backend;
  private final PlatformLog log;
  private final Set<Repeating> repeating = ConcurrentHashMap.newKeySet();
  private volatile int generation;

  public BukkitScheduler(SchedulerBackend backend, PlatformLog log) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.log = Objects.requireNonNull(log, "log");
  }

  @Override
  public boolean isRegionized() {
    return backend.isRegionized();
  }

  @Override
  public boolean isTickThread() {
    return backend.isTickThread();
  }

  @Override
  public boolean owns(EntityRef entity) {
    Entity handle = BukkitRefs.entity(entity);
    return handle != null && backend.owns(handle);
  }

  @Override
  public boolean owns(Loc location) {
    Location target = BukkitRefs.location(location);
    return target != null && backend.owns(target);
  }

  @Override
  public boolean owns(WorldRef world, int chunkX, int chunkZ) {
    World handle = BukkitRefs.world(world);
    return handle != null && backend.owns(handle, chunkX, chunkZ);
  }

  @Override
  public boolean ownsArea(Loc center, double radiusX, double radiusZ) {
    Location target = BukkitRefs.location(center);
    return target != null && backend.ownsArea(target, radiusX, radiusZ);
  }

  @Override
  public boolean runEntity(EntityRef entity, Runnable task, long delayTicks, Runnable retired) {
    Objects.requireNonNull(task, "task");
    Entity handle = entity == null ? null : BukkitRefs.entity(entity);
    if (handle == null) {
      return false;
    }
    int scheduled = generation;
    return backend.runEntity(handle, guard(scheduled, task), delayTicks, retired == null ? null : guard(scheduled, retired));
  }

  @Override
  public boolean runAt(Loc location, Runnable task, long delayTicks) {
    Objects.requireNonNull(task, "task");
    Location target = BukkitRefs.location(location);
    return target != null && backend.runAt(target, guard(generation, task), delayTicks);
  }

  @Override
  public boolean runGlobal(Runnable task, long delayTicks) {
    Objects.requireNonNull(task, "task");
    return backend.runGlobal(guard(generation, task), delayTicks);
  }

  @Override
  public TaskHandle runAsync(Runnable task, long delayTicks) {
    Objects.requireNonNull(task, "task");
    Once once = new Once(generation);
    backend.runAsync(() -> {
      if (!once.isCancelled()) {
        run(task);
      }
    }, delayTicks);
    return once;
  }

  @Override
  public TaskHandle repeatEntity(EntityRef entity, Runnable task, long delayTicks, long periodTicks, Runnable retired) {
    Objects.requireNonNull(task, "task");
    Entity handle = entity == null ? null : BukkitRefs.entity(entity);
    Repeating repeat = new Repeating(task, periodTicks, retired) {
      @Override
      boolean arm(long delay) {
        return backend.runEntity(handle, this::fire, delay, this::retire);
      }
    };
    return start(repeat, handle == null ? -1L : delayTicks);
  }

  @Override
  public TaskHandle repeatAt(Loc location, Runnable task, long delayTicks, long periodTicks) {
    Objects.requireNonNull(task, "task");
    Location target = location == null ? null : BukkitRefs.location(location);
    Repeating repeat = new Repeating(task, periodTicks, null) {
      @Override
      boolean arm(long delay) {
        return backend.runAt(target, this::fire, delay);
      }
    };
    return start(repeat, target == null ? -1L : delayTicks);
  }

  @Override
  public TaskHandle repeatGlobal(Runnable task, long delayTicks, long periodTicks) {
    Objects.requireNonNull(task, "task");
    Repeating repeat = new Repeating(task, periodTicks, null) {
      @Override
      boolean arm(long delay) {
        return backend.runGlobal(this::fire, delay);
      }
    };
    return start(repeat, delayTicks);
  }

  @Override
  public TaskHandle repeatAsync(Runnable task, long delayTicks, long periodTicks) {
    Objects.requireNonNull(task, "task");
    Repeating repeat = new Repeating(task, periodTicks, null) {
      @Override
      boolean arm(long delay) {
        backend.runAsync(this::fire, delay);
        return true;
      }
    };
    return start(repeat, delayTicks);
  }

  @Override
  public void cancelAll() {
    generation++;
    for (Repeating repeat : repeating) {
      repeat.cancel();
    }
    repeating.clear();
  }

  /** Repeating tasks still scheduled. */
  public int activeRepeating() {
    return repeating.size();
  }

  private TaskHandle start(Repeating repeat, long delayTicks) {
    if (delayTicks < 0L) {
      repeat.cancel();
      return repeat;
    }
    repeating.add(repeat);
    if (!repeat.arm(delayTicks)) {
      repeat.cancel();
    }
    return repeat;
  }

  private Runnable guard(int scheduled, Runnable task) {
    return () -> {
      if (generation == scheduled) {
        run(task);
      }
    };
  }

  private void run(Runnable task) {
    try {
      task.run();
    } catch (VirtualMachineError fatal) {
      throw fatal;
    } catch (Throwable failure) {
      log.error("Scheduled task failed", failure);
    }
  }

  private final class Once implements TaskHandle {
    private final int scheduled;
    private volatile boolean cancelled;

    private Once(int scheduled) {
      this.scheduled = scheduled;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled || generation != scheduled;
    }
  }

  private abstract class Repeating implements TaskHandle {
    private final Runnable task;
    private final long period;
    private final Runnable retired;
    private volatile boolean cancelled;

    Repeating(Runnable task, long period, Runnable retired) {
      this.task = task;
      this.period = Math.max(1L, period);
      this.retired = retired;
    }

    abstract boolean arm(long delay);

    void fire() {
      if (cancelled) {
        return;
      }
      run(task);
      if (cancelled) {
        return;
      }
      if (!arm(period)) {
        cancel();
      }
    }

    void retire() {
      if (cancelled) {
        return;
      }
      cancel();
      if (retired != null) {
        run(retired);
      }
    }

    @Override
    public void cancel() {
      cancelled = true;
      repeating.remove(this);
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }
  }
}
