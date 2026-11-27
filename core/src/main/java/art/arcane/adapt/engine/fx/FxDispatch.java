package art.arcane.adapt.engine.fx;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class FxDispatch {
  private static final int MAX_PENDING_COMMANDS = FxBudget.PER_VIEWER_EMISSION_CAP * 2;

  private FxDispatch() {
  }

  static Emission emission(Command command, Consumer<Throwable> failureHandler) {
    return new Emission(command, failureHandler);
  }

  static void dispatch(PlayerRef player, Emission emission) {
    if (player == null || emission == null) {
      return;
    }
    new ViewerBatch(player).enqueue(emission);
  }

  static final class ViewerBatch {
    private final PlayerRef player;
    private final ConcurrentLinkedQueue<Emission> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    ViewerBatch(PlayerRef player) {
      this.player = Objects.requireNonNull(player);
    }

    boolean enqueue(Emission emission) {
      if (emission == null || !reserve()) {
        return false;
      }

      pending.add(emission);
      schedule();
      return true;
    }

    int pendingCount() {
      return pendingCount.get();
    }

    private boolean reserve() {
      while (true) {
        int current = pendingCount.get();
        if (current >= MAX_PENDING_COMMANDS) {
          return false;
        }
        if (pendingCount.compareAndSet(current, current + 1)) {
          return true;
        }
      }
    }

    private void schedule() {
      if (!scheduled.compareAndSet(false, true)) {
        return;
      }

      while (true) {
        boolean accepted;
        try {
          accepted = Tasks.runEntity(player, this::drain);
        } catch (Throwable error) {
          failPending(error);
          accepted = false;
        }
        if (accepted) {
          return;
        }
        discardPending();
        scheduled.set(false);
        if (pending.isEmpty() || !scheduled.compareAndSet(false, true)) {
          return;
        }
      }
    }

    private void drain() {
      while (true) {
        Emission emission;
        while ((emission = pending.poll()) != null) {
          pendingCount.decrementAndGet();
          emission.emit(player);
        }
        scheduled.set(false);
        if (pending.isEmpty() || !scheduled.compareAndSet(false, true)) {
          return;
        }
      }
    }

    private void failPending(Throwable error) {
      Emission emission;
      while ((emission = pending.poll()) != null) {
        pendingCount.decrementAndGet();
        emission.fail(error);
      }
    }

    private void discardPending() {
      while (pending.poll() != null) {
        pendingCount.decrementAndGet();
      }
    }
  }

  static final class Emission {
    private final Command command;
    private final Consumer<Throwable> failureHandler;
    private final AtomicBoolean failed = new AtomicBoolean();

    private Emission(Command command, Consumer<Throwable> failureHandler) {
      this.command = Objects.requireNonNull(command);
      this.failureHandler = failureHandler;
    }

    private void emit(PlayerRef player) {
      if (failed.get()) {
        return;
      }
      try {
        command.emit(player);
      } catch (Throwable error) {
        fail(error);
      }
    }

    private void fail(Throwable error) {
      if (!failed.compareAndSet(false, true)) {
        return;
      }
      if (failureHandler != null) {
        try {
          failureHandler.accept(error);
          return;
        } catch (Throwable handlerError) {
          AdaptLog.error("FX failure handler threw while reporting a viewer dispatch error.");
          AdaptLog.error(error);
          AdaptLog.error(handlerError);
          return;
        }
      }
      AdaptLog.error("FX viewer dispatch failed; dropping the affected emission.");
      AdaptLog.error(error);
    }
  }

  @FunctionalInterface
  interface Command {
    void emit(PlayerRef player);
  }
}
