package art.arcane.adapt.api.golem.gui;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class LiveRefresh<S> {
  static final int POLL_TICKS = 10;

  private final Hooks<S> hooks;
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean ended = new AtomicBoolean();

  LiveRefresh(Hooks<S> hooks) {
    this.hooks = Objects.requireNonNull(hooks);
  }

  void start() {
    if (started.compareAndSet(false, true)) {
      arm();
    }
  }

  void stop() {
    ended.set(true);
  }

  private void arm() {
    if (ended.get()) {
      return;
    }
    if (!hooks.reader().run(this::read, POLL_TICKS, this::lost)) {
      lost();
    }
  }

  private void read() {
    if (ended.get()) {
      return;
    }
    Optional<S> state = hooks.read().get();
    if (state.isEmpty()) {
      lost();
      return;
    }
    S next = state.get();
    if (!hooks.viewer().run(() -> deliver(next), 0, this::stop)) {
      stop();
    }
  }

  private void deliver(S next) {
    if (ended.get() || !hooks.open().getAsBoolean()) {
      stop();
      return;
    }
    hooks.show().accept(next);
    if (!hooks.open().getAsBoolean()) {
      stop();
      return;
    }
    arm();
  }

  private void lost() {
    if (ended.get()) {
      return;
    }
    if (!hooks.viewer().run(this::report, 0, this::stop)) {
      stop();
    }
  }

  private void report() {
    if (ended.getAndSet(true)) {
      return;
    }
    if (hooks.open().getAsBoolean()) {
      hooks.gone().run();
    }
  }

  @FunctionalInterface
  interface Scheduler {
    boolean run(Runnable task, int delayTicks, Runnable retired);
  }

  record Hooks<S>(Scheduler reader, Scheduler viewer, Supplier<Optional<S>> read, Consumer<S> show, Runnable gone, BooleanSupplier open) {
  }
}
