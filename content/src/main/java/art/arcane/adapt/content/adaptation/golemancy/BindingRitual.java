package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.content.golem.GolemRitualMarker;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class BindingRitual {
  private static final long STALE_AFTER_MILLIS = 5_000L;
  private static final double HOLD_TOLERANCE = 0.1D;

  private final BindingTarget target;
  private final int durationTicks;
  private final Loc anchor;
  private final boolean wasAware;
  private final AtomicReference<ItemRef> core;
  private final AtomicBoolean finished = new AtomicBoolean();
  private volatile FxTimeline timeline;
  private volatile long lastPulseMillis;
  private int elapsedTicks;

  BindingRitual(BindingTarget target, int durationTicks, long startedAtMillis, ItemRef core) {
    this.target = Objects.requireNonNull(target);
    this.durationTicks = Math.max(1, durationTicks);
    this.core = new AtomicReference<>(core);
    this.anchor = target.golem().location();
    this.wasAware = GolemRitualMarker.wasAware(target.golem()).orElse(target.golem().isAware());
    this.lastPulseMillis = startedAtMillis;
  }

  BindingTarget target() {
    return target;
  }

  boolean wasAware() {
    return wasAware;
  }

  ItemRef takeCore() {
    return core.getAndSet(null);
  }

  Loc anchor() {
    return anchor;
  }

  void attach(FxTimeline started) {
    timeline = started;
  }

  void pulsed(long nowMillis) {
    lastPulseMillis = nowMillis;
  }

  void advance() {
    elapsedTicks = Math.min(durationTicks, elapsedTicks + 1);
  }

  double progress() {
    return (double) elapsedTicks / durationTicks;
  }

  boolean complete() {
    return elapsedTicks >= durationTicks;
  }

  boolean finish() {
    if (!finished.compareAndSet(false, true)) {
      return false;
    }
    FxTimeline local = timeline;
    if (local != null) {
      local.cancel();
    }
    return true;
  }

  boolean isFinished() {
    return finished.get();
  }

  boolean stale(long nowMillis) {
    return nowMillis - lastPulseMillis > STALE_AFTER_MILLIS;
  }

  boolean drifted(Loc current) {
    return anchor.sameWorld(current) && anchor.distanceSquared(current) > HOLD_TOLERANCE * HOLD_TOLERANCE;
  }

  Loc anchorFacing(Loc current) {
    return anchor.withRotation(current.yaw(), current.pitch());
  }

  boolean withinReach(double reach) {
    Loc golemAt = target.golem().location();
    Loc ownerAt = target.owner().location();
    return golemAt.sameWorld(ownerAt) && golemAt.distanceSquared(ownerAt) <= reach * reach;
  }
}
