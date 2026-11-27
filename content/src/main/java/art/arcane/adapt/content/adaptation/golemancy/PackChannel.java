package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class PackChannel {
  private static final long STALE_AFTER_MILLIS = 5_000L;

  private final PlayerRef owner;
  private final GolemHandle handle;
  private final int durationTicks;
  private final int heldSlot;
  private final AtomicBoolean finished = new AtomicBoolean();
  private volatile FxTimeline timeline;
  private volatile long lastPulseMillis;
  private int elapsedTicks;

  PackChannel(PlayerRef owner, GolemHandle handle, int durationTicks, long startedAtMillis) {
    this.owner = Objects.requireNonNull(owner);
    this.handle = Objects.requireNonNull(handle);
    this.durationTicks = Math.max(1, durationTicks);
    this.heldSlot = owner.inventory().heldSlot();
    this.lastPulseMillis = startedAtMillis;
  }

  PlayerRef owner() {
    return owner;
  }

  GolemHandle handle() {
    return handle;
  }

  MobRef golem() {
    return handle.entity();
  }

  int durationTicks() {
    return durationTicks;
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

  boolean stillHeld() {
    return owner.inventory().heldSlot() == heldSlot
        && GolemItems.kindOf(owner.inventory().mainHand()).orElse(null) == GolemItemKind.CROOK;
  }

  boolean withinReach(double reach) {
    Loc golemAt = golem().location();
    Loc ownerAt = owner.location();
    return golemAt.sameWorld(ownerAt) && golemAt.distanceSquared(ownerAt) <= reach * reach;
  }
}
