package art.arcane.adapt.content.adaptation.ranged;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

final class TrajectorySightBudget {
  private final int renderLimit;
  private final int rayTraceLimit;
  private final long windowNanos;
  private final LongSupplier clock;
  private final ArrayDeque<UUID> waiting = new ArrayDeque<>();
  private final Set<UUID> waitingIds = new HashSet<>();
  private final Set<UUID> reserved = new HashSet<>();
  private final Set<UUID> rendered = new HashSet<>();
  private long window = Long.MIN_VALUE;
  private int renderCount;
  private int rayTraceCount;

  TrajectorySightBudget(int renderLimit, int rayTraceLimit, long windowNanos) {
    this(renderLimit, rayTraceLimit, windowNanos, System::nanoTime);
  }

  TrajectorySightBudget(int renderLimit, int rayTraceLimit, long windowNanos, LongSupplier clock) {
    if (renderLimit <= 0 || rayTraceLimit <= 0 || windowNanos <= 0) {
      throw new IllegalArgumentException("Trajectory sight budget limits must be positive");
    }
    this.renderLimit = renderLimit;
    this.rayTraceLimit = rayTraceLimit;
    this.windowNanos = windowNanos;
    this.clock = Objects.requireNonNull(clock);
  }

  synchronized boolean tryAcquireRender(UUID playerId) {
    Objects.requireNonNull(playerId);
    rotateWindow();
    if (rendered.contains(playerId)) {
      return false;
    }

    if (reserved.remove(playerId)) {
      rendered.add(playerId);
      renderCount++;
      return true;
    }

    if (!reserved.isEmpty() || !waiting.isEmpty() || renderCount >= renderLimit) {
      enqueue(playerId);
      return false;
    }

    rendered.add(playerId);
    renderCount++;
    return true;
  }

  synchronized boolean tryAcquireRayTrace() {
    rotateWindow();
    if (rayTraceCount >= rayTraceLimit) {
      return false;
    }
    rayTraceCount++;
    return true;
  }

  synchronized void cancel(UUID playerId) {
    if (playerId == null) {
      return;
    }
    rotateWindow();
    waitingIds.remove(playerId);
    waiting.remove(playerId);
    boolean releasedReservation = reserved.remove(playerId);
    rendered.remove(playerId);
    if (releasedReservation) {
      promoteWaiting();
    }
  }

  synchronized int renderCount() {
    rotateWindow();
    return renderCount;
  }

  synchronized int rayTraceCount() {
    rotateWindow();
    return rayTraceCount;
  }

  synchronized int waitingCount() {
    rotateWindow();
    return waiting.size();
  }

  private void rotateWindow() {
    long currentWindow = Math.floorDiv(clock.getAsLong(), windowNanos);
    if (currentWindow == window) {
      return;
    }

    window = currentWindow;
    renderCount = 0;
    rayTraceCount = 0;
    rendered.clear();
    reserved.clear();
    while (reserved.size() < renderLimit && !waiting.isEmpty()) {
      UUID playerId = waiting.removeFirst();
      waitingIds.remove(playerId);
      reserved.add(playerId);
    }
  }

  private void enqueue(UUID playerId) {
    if (reserved.contains(playerId) || rendered.contains(playerId) || !waitingIds.add(playerId)) {
      return;
    }
    waiting.addLast(playerId);
  }

  private void promoteWaiting() {
    if (reserved.size() + renderCount >= renderLimit || waiting.isEmpty()) {
      return;
    }
    UUID playerId = waiting.removeFirst();
    waitingIds.remove(playerId);
    reserved.add(playerId);
  }
}

final class TrajectorySightSessionGate {
  private final AtomicBoolean running = new AtomicBoolean();
  private final AtomicLong generation = new AtomicLong();

  long start() {
    if (!running.compareAndSet(false, true)) {
      return -1L;
    }
    return generation.incrementAndGet();
  }

  boolean isCurrent(long token) {
    return running.get() && generation.get() == token;
  }

  boolean isRunning() {
    return running.get();
  }

  void stop() {
    if (running.getAndSet(false)) {
      generation.incrementAndGet();
    }
  }
}
