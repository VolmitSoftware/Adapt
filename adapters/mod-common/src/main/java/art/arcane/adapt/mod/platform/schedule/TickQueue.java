package art.arcane.adapt.mod.platform.schedule;

import art.arcane.adapt.platform.TaskHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Tick-counted task queue. Any thread may schedule; only the ticking thread runs tasks. A task
 * scheduled with delay {@code d} during tick {@code t} (or between ticks {@code t} and {@code t+1})
 * runs during tick {@code t + max(1, d)}. Repeating tasks then run every {@code max(1, period)}
 * ticks. Tasks due on the same tick run in scheduling order.
 */
public final class TickQueue {
    private final ConcurrentLinkedQueue<Entry> incoming = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<Entry> pending = new PriorityQueue<>();
    private final AtomicLong tick = new AtomicLong();
    private final AtomicLong sequence = new AtomicLong();
    private final Consumer<Throwable> failures;
    private volatile boolean closed;

    public TickQueue(Consumer<Throwable> failures) {
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    public long currentTick() {
        return tick.get();
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Schedules a task. {@code alive} is checked before each run; when it reports false the task is
     * cancelled and {@code retired} runs instead (once). Returns null when the queue is closed.
     */
    public Entry schedule(Runnable task, long delayTicks, long periodTicks, BooleanSupplier alive, Runnable retired) {
        Objects.requireNonNull(task, "task");
        if (closed) {
            return null;
        }
        Entry entry = new Entry(task, Math.max(0L, periodTicks), alive, retired, sequence.getAndIncrement());
        entry.due = tick.get() + Math.max(1L, delayTicks);
        incoming.add(entry);
        return entry;
    }

    public Entry schedule(Runnable task, long delayTicks, long periodTicks) {
        return schedule(task, delayTicks, periodTicks, null, null);
    }

    /** Advances one tick and runs every due task. Must be called from one thread only. */
    public void tick() {
        if (closed) {
            return;
        }
        long now = tick.incrementAndGet();
        Entry added;
        while ((added = incoming.poll()) != null) {
            pending.add(added);
        }
        List<Entry> again = null;
        Entry head;
        while ((head = pending.peek()) != null && head.due <= now) {
            pending.poll();
            if (head.isCancelled()) {
                continue;
            }
            if (head.alive != null && !head.alive.getAsBoolean()) {
                head.retire(failures);
                continue;
            }
            run(head.task);
            if (head.period > 0L && !head.isCancelled() && !closed) {
                head.due = now + head.period;
                if (again == null) {
                    again = new ArrayList<>();
                }
                again.add(head);
            } else {
                head.cancelled.set(true);
            }
            if (closed) {
                return;
            }
        }
        if (again != null) {
            pending.addAll(again);
        }
    }

    public int size() {
        return pending.size() + incoming.size();
    }

    /** Cancels every task. The queue keeps accepting new tasks unless {@code close} is true. */
    public void cancelAll(boolean close) {
        if (close) {
            closed = true;
        }
        Entry entry;
        while ((entry = incoming.poll()) != null) {
            entry.cancel();
        }
        for (Entry queued : pending) {
            queued.cancel();
        }
        pending.clear();
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            failures.accept(failure);
        }
    }

    public static final class Entry implements TaskHandle, Comparable<Entry> {
        private final Runnable task;
        private final long period;
        private final BooleanSupplier alive;
        private final Runnable retired;
        private final long order;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private long due;

        private Entry(Runnable task, long period, BooleanSupplier alive, Runnable retired, long order) {
            this.task = task;
            this.period = period;
            this.alive = alive;
            this.retired = retired;
            this.order = order;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        private void retire(Consumer<Throwable> failures) {
            if (!cancelled.compareAndSet(false, true) || retired == null) {
                return;
            }
            try {
                retired.run();
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable failure) {
                failures.accept(failure);
            }
        }

        @Override
        public int compareTo(Entry other) {
            int byTick = Long.compare(due, other.due);
            return byTick != 0 ? byTick : Long.compare(order, other.order);
        }
    }
}
