package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.golem.GolemRitualMarker;
import art.arcane.adapt.api.golem.item.GolemItems;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class BindingRitual {
  private static final long STALE_AFTER_MILLIS = 5_000L;

  private final BindingTarget target;
  private final int durationTicks;
  private final int heldSlot;
  private final boolean wasAware;
  private final AtomicBoolean finished = new AtomicBoolean();
  private volatile FxTimeline timeline;
  private volatile long lastPulseMillis;
  private int elapsedTicks;

  BindingRitual(BindingTarget target, int durationTicks, long startedAtMillis) {
    this.target = Objects.requireNonNull(target);
    this.durationTicks = Math.max(1, durationTicks);
    this.heldSlot = target.owner().getInventory().getHeldItemSlot();
    this.wasAware = GolemRitualMarker.wasAware(target.golem()).orElse(target.golem().isAware());
    this.lastPulseMillis = startedAtMillis;
  }

  BindingTarget target() {
    return target;
  }

  boolean wasAware() {
    return wasAware;
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

  boolean stillHeld() {
    Player owner = target.owner();
    return owner.getInventory().getHeldItemSlot() == heldSlot
        && GolemItems.coreOf(owner.getInventory().getItemInMainHand()).orElse(null) == target.core();
  }

  boolean withinReach(double reach) {
    Location golemAt = target.golem().getLocation();
    Location ownerAt = target.owner().getLocation();
    return Objects.equals(golemAt.getWorld(), ownerAt.getWorld()) && golemAt.distanceSquared(ownerAt) <= reach * reach;
  }
}
