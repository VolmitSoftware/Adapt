package art.arcane.adapt.mod.platform.schedule;

import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Single-threaded server scheduler. Sync work runs on the server thread from {@link #tick()}; async
 * work is released by the same tick counter onto a daemon pool.
 */
public final class ModScheduler implements AdaptScheduler {
    private static final int ASYNC_THREADS = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));

    private final TickQueue queue;
    private final PlatformLog log;
    private final ThreadPoolExecutor async;
    private volatile Thread serverThread;

    public ModScheduler(PlatformLog log) {
        this.log = Objects.requireNonNull(log, "log");
        this.queue = new TickQueue((Throwable failure) -> log.error("Scheduled task failed", failure));
        this.async = new ThreadPoolExecutor(ASYNC_THREADS, ASYNC_THREADS, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), new AsyncThreads());
        this.async.allowCoreThreadTimeOut(true);
    }

    /** Binds the server thread. Called from the server thread when the server starts. */
    public void bind(Thread thread) {
        serverThread = thread;
    }

    /** Runs due tasks. Server thread only. */
    public void tick() {
        queue.tick();
    }

    public long currentTick() {
        return queue.currentTick();
    }

    public void shutdown() {
        queue.cancelAll(true);
        List<Runnable> dropped = async.shutdownNow();
        if (!dropped.isEmpty()) {
            log.verbose("Dropped " + dropped.size() + " async tasks on shutdown");
        }
        serverThread = null;
    }

    @Override
    public boolean isRegionized() {
        return false;
    }

    @Override
    public boolean isTickThread() {
        Thread thread = serverThread;
        return thread != null && Thread.currentThread() == thread;
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

    /** Without a delay on the server thread the task runs at once, as Paper's entity scheduling does for an owned entity. */
    @Override
    public boolean runEntity(EntityRef entity, Runnable task, long delayTicks, Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        BooleanSupplier alive = alive(entity);
        if (delayTicks <= 0L && isTickThread()) {
            if (retired != null && !alive.getAsBoolean()) {
                retired.run();
            } else {
                task.run();
            }
            return true;
        }
        if (!alive.getAsBoolean()) {
            return false;
        }
        return queue.schedule(task, delayTicks, 0L, alive, retired) != null;
    }

    @Override
    public boolean runAt(Loc location, Runnable task, long delayTicks) {
        return queue.schedule(task, delayTicks, 0L) != null;
    }

    @Override
    public boolean runGlobal(Runnable task, long delayTicks) {
        return queue.schedule(task, delayTicks, 0L) != null;
    }

    @Override
    public void runAsync(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        if (delayTicks <= 0L) {
            submitAsync(task);
            return;
        }
        queue.schedule(() -> submitAsync(task), delayTicks, 0L);
    }

    @Override
    public TaskHandle repeatEntity(EntityRef entity, Runnable task, long delayTicks, long periodTicks, Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        BooleanSupplier alive = alive(entity);
        TickQueue.Entry entry = queue.schedule(task, delayTicks, Math.max(1L, periodTicks), alive, retired);
        return entry == null ? CancelledTask.INSTANCE : entry;
    }

    @Override
    public TaskHandle repeatGlobal(Runnable task, long delayTicks, long periodTicks) {
        TickQueue.Entry entry = queue.schedule(task, delayTicks, Math.max(1L, periodTicks));
        return entry == null ? CancelledTask.INSTANCE : entry;
    }

    @Override
    public TaskHandle repeatAsync(Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        TickQueue.Entry entry = queue.schedule(() -> submitAsync(task), delayTicks, Math.max(1L, periodTicks));
        return entry == null ? CancelledTask.INSTANCE : entry;
    }

    @Override
    public void cancelAll() {
        queue.cancelAll(false);
    }

    /** Players survive death and respawn, so their tasks retire on quit; other entities retire when invalid. */
    private static BooleanSupplier alive(EntityRef entity) {
        return entity instanceof PlayerRef player ? player::isOnline : entity::isValid;
    }

    private void submitAsync(Runnable task) {
        try {
            async.execute(() -> {
                try {
                    task.run();
                } catch (VirtualMachineError fatal) {
                    throw fatal;
                } catch (Throwable failure) {
                    log.error("Async task failed", failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            log.verbose("Async task rejected: scheduler is shut down");
        }
    }

    private enum CancelledTask implements TaskHandle {
        INSTANCE;

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    }

    private static final class AsyncThreads implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Adapt Async " + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
