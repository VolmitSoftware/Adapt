package art.arcane.adapt.api.golem.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LiveRefreshTest {
  private final ManualScheduler scheduler = new ManualScheduler();
  private final Object source = new Object();
  private final Object viewer = new Object();
  private final AtomicReference<Optional<String>> state = new AtomicReference<>(Optional.of("first"));
  private final AtomicBoolean open = new AtomicBoolean(true);
  private final AtomicInteger reads = new AtomicInteger();
  private final List<String> shown = new ArrayList<>();
  private final AtomicInteger gone = new AtomicInteger();
  private final AtomicReference<Runnable> duringRead = new AtomicReference<>(() -> {
  });
  private final LiveRefresh<String> live = new LiveRefresh<>(new LiveRefresh.Hooks<>(
      scheduler.scheduler(source), scheduler.scheduler(viewer),
      () -> {
        scheduler.expect(source, "read");
        reads.incrementAndGet();
        duringRead.get().run();
        return state.get();
      },
      next -> {
        scheduler.expect(viewer, "show");
        shown.add(next);
      },
      () -> {
        scheduler.expect(viewer, "gone");
        gone.incrementAndGet();
      },
      open::get));

  @Test
  void nothingIsScheduledUntilTheWindowStartsIt() {
    scheduler.advance(1000);

    assertThat(scheduler.pending()).isZero();
    assertThat(reads).hasValue(0);
  }

  @Test
  void aStateReadOnTheSourceSchedulerIsShownOnTheViewerScheduler() {
    live.start();

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(shown).containsExactly("first");
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void theFirstReadWaitsOnePollInterval() {
    live.start();

    scheduler.advance(LiveRefresh.POLL_TICKS - 1);

    assertThat(reads).hasValue(0);
  }

  @Test
  void theLoopReadsOncePerPollInterval() {
    live.start();

    scheduler.advance(LiveRefresh.POLL_TICKS * 5);

    assertThat(reads).hasValue(5);
  }

  @Test
  void aLaterStateIsDeliveredWhenItChanges() {
    live.start();
    scheduler.advance(LiveRefresh.POLL_TICKS);
    state.set(Optional.of("second"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(shown).containsExactly("first", "second");
  }

  @Test
  void startingTwiceRunsOneLoop() {
    live.start();
    live.start();

    scheduler.advance(LiveRefresh.POLL_TICKS);

    assertThat(reads).hasValue(1);
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void aClosedWindowEndsTheLoopWithoutShowingAnything() {
    live.start();
    scheduler.advance(LiveRefresh.POLL_TICKS);
    open.set(false);

    scheduler.advance(LiveRefresh.POLL_TICKS * 10);

    assertThat(shown).containsExactly("first");
    assertThat(reads).hasValue(2);
    assertThat(scheduler.pending()).isZero();
    assertThat(gone).hasValue(0);
  }

  @Test
  void aStopBeforeTheNextReadSkipsTheRead() {
    live.start();
    live.stop();

    scheduler.advance(LiveRefresh.POLL_TICKS * 10);

    assertThat(reads).hasValue(0);
    assertThat(shown).isEmpty();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aStopBetweenTheReadAndTheDeliveryShowsNothing() {
    duringRead.set(live::stop);
    live.start();

    scheduler.advance(LiveRefresh.POLL_TICKS * 10);

    assertThat(reads).hasValue(1);
    assertThat(shown).isEmpty();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aSourceThatIsGoneClosesTheViewOnceAndEndsTheLoop() {
    live.start();
    state.set(Optional.empty());

    scheduler.advance(LiveRefresh.POLL_TICKS * 10);

    assertThat(gone).hasValue(1);
    assertThat(shown).isEmpty();
    assertThat(scheduler.pending()).isZero();
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aSourceThatIsGoneAfterTheWindowClosedStaysSilent() {
    live.start();
    state.set(Optional.empty());
    open.set(false);

    scheduler.advance(LiveRefresh.POLL_TICKS * 10);

    assertThat(gone).hasValue(0);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aSourceSchedulerThatRetiresTheTaskClosesTheViewOnce() {
    live.start();

    scheduler.retire(source);
    scheduler.advance(LiveRefresh.POLL_TICKS * 10);

    assertThat(gone).hasValue(1);
    assertThat(reads).hasValue(0);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aViewerSchedulerThatRetiresTheDeliveryEndsTheLoopQuietly() {
    List<Runnable> retirements = new ArrayList<>();
    LiveRefresh<String> held = new LiveRefresh<>(new LiveRefresh.Hooks<>(
        scheduler.scheduler(source), (task, delay, retired) -> retirements.add(retired),
        () -> state.get(), shown::add, gone::incrementAndGet, open::get));
    held.start();
    scheduler.advance(LiveRefresh.POLL_TICKS);

    retirements.getFirst().run();
    scheduler.advance(LiveRefresh.POLL_TICKS * 10);

    assertThat(gone).hasValue(0);
    assertThat(shown).isEmpty();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aSourceSchedulerThatRefusesTheTaskClosesTheView() {
    LiveRefresh<String> refused = new LiveRefresh<>(new LiveRefresh.Hooks<>(
        (task, delay, retired) -> false, scheduler.scheduler(viewer),
        () -> state.get(), shown::add, gone::incrementAndGet, open::get));

    refused.start();
    scheduler.advance(1);

    assertThat(gone).hasValue(1);
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aViewShownThatClosesTheWindowEndsTheLoop() {
    LiveRefresh<String> closing = new LiveRefresh<>(new LiveRefresh.Hooks<>(
        scheduler.scheduler(source), scheduler.scheduler(viewer),
        () -> state.get(), next -> open.set(false), gone::incrementAndGet, open::get));

    closing.start();
    scheduler.advance(LiveRefresh.POLL_TICKS * 10);

    assertThat(scheduler.pending()).isZero();
    assertThat(gone).hasValue(0);
  }
}
