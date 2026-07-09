package art.arcane.adapt.api.fx;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.tick.TickedObject;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class FxDirector extends TickedObject {
  private static final int MAX_ACTIVE_TIMELINES = 2048;
  private static final int MAX_CRITICAL_TIMELINES = 2560;

  private static volatile FxDirector active;

  private final ConcurrentLinkedQueue<FxTimeline> incoming;
  private final ArrayList<FxTimeline> timelines;
  private final AtomicInteger activeCount;

  public FxDirector() {
    super("fx", "fx-director", 50);
    this.incoming = new ConcurrentLinkedQueue<>();
    this.timelines = new ArrayList<>(64);
    this.activeCount = new AtomicInteger();
    active = this;
  }

  static FxDirector active() {
    return active;
  }

  @Override
  public void onTick() {
    FxBudget.resetTick();
    FxViewers.bumpTick();

    FxTimeline queued;
    while ((queued = incoming.poll()) != null) {
      timelines.add(queued);
    }

    int size = timelines.size();
    if (size == 0) {
      return;
    }

    int keep = 0;
    for (int i = 0; i < size; i++) {
      FxTimeline timeline = timelines.get(i);
      boolean alive;
      try {
        alive = timeline.advance();
      } catch (Throwable e) {
        alive = false;
        timeline.cancel();
        Adapt.error("Fx timeline for '" + timeline.label() + "' threw during a frame; cancelling it.");
        e.printStackTrace();
      }

      if (alive) {
        timelines.set(keep, timeline);
        keep++;
      } else {
        activeCount.decrementAndGet();
      }
    }

    for (int i = timelines.size() - 1; i >= keep; i--) {
      timelines.remove(i);
    }
  }

  void register(FxTimeline timeline) {
    FxPriority priority = timeline.priority();
    int cap = priority == FxPriority.GAMEPLAY || priority == FxPriority.COMBAT ? MAX_CRITICAL_TIMELINES : MAX_ACTIVE_TIMELINES;
    if (activeCount.get() >= cap) {
      return;
    }

    activeCount.incrementAndGet();
    incoming.add(timeline);
  }
}
