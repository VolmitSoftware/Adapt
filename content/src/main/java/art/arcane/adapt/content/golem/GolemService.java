package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.event.EventSubscription;
import art.arcane.adapt.platform.TaskHandle;

import java.util.ArrayList;
import java.util.List;

/**
 * A Golemancy service that listens on Adapt's event bus while active and, when built with an interval, ticks on the
 * global thread at that cadence whenever it has work.
 */
public abstract class GolemService {
  private static final long MILLIS_PER_TICK = 50L;

  private final boolean ticking;
  private final List<EventSubscription> subscriptions = new ArrayList<>();
  private long intervalMillis;
  private TaskHandle ticks;
  private boolean active;
  private boolean retired;

  /** A service that only listens. */
  protected GolemService() {
    this.ticking = false;
  }

  /** A service that listens and ticks every {@code intervalMillis}, at least once a server tick. */
  protected GolemService(long intervalMillis) {
    this.ticking = true;
    this.intervalMillis = intervalMillis;
  }

  /** Subscribes this service and its companion listeners and starts ticking; a retired service stays inactive. */
  public final synchronized void activateRuntime() {
    if (retired || active) {
      return;
    }
    active = true;
    try {
      subscriptions.add(AdaptApi.events().subscribe(this));
      for (Object companion : createCompanionListeners()) {
        subscriptions.add(AdaptApi.events().subscribe(companion));
      }
      if (ticking) {
        schedule();
      }
    } catch (RuntimeException | Error error) {
      stop();
      throw error;
    }
  }

  /** Unsubscribes the service and stops its ticks for good. */
  public final synchronized void unregister() {
    retired = true;
    stop();
  }

  /** Changes the tick cadence; an active service reschedules at once. */
  public final synchronized void setInterval(long intervalMillis) {
    if (this.intervalMillis == intervalMillis) {
      return;
    }
    this.intervalMillis = intervalMillis;
    if (active && ticks != null) {
      ticks.cancel();
      schedule();
    }
  }

  /** Whether the next tick has anything to do; a service without demand skips it. */
  public boolean hasTickDemand() {
    return true;
  }

  /** One tick of work, on the global thread. */
  public void onTick() {
  }

  /** Extra listeners subscribed and retired with this service. */
  protected List<?> createCompanionListeners() {
    return List.of();
  }

  private void schedule() {
    long period = Math.max(1L, (intervalMillis + MILLIS_PER_TICK - 1L) / MILLIS_PER_TICK);
    ticks = AdaptApi.scheduler().repeatGlobal(this::tick, period, period);
  }

  private void tick() {
    try {
      if (hasTickDemand()) {
        onTick();
      }
    } catch (RuntimeException error) {
      AdaptApi.log().error("Golemancy service " + getClass().getSimpleName() + " failed to tick", error);
    }
  }

  private void stop() {
    active = false;
    if (ticks != null) {
      ticks.cancel();
      ticks = null;
    }
    for (EventSubscription subscription : subscriptions) {
      subscription.unsubscribe();
    }
    subscriptions.clear();
  }
}
