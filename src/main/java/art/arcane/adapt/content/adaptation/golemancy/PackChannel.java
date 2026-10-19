package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class PackChannel {
  private static final long STALE_AFTER_MILLIS = 5_000L;

  private final Player owner;
  private final GolemHandle handle;
  private final int durationTicks;
  private final int heldSlot;
  private final AtomicBoolean finished = new AtomicBoolean();
  private volatile FxTimeline timeline;
  private volatile long lastPulseMillis;
  private int elapsedTicks;

  PackChannel(Player owner, GolemHandle handle, int durationTicks, long startedAtMillis) {
    this.owner = Objects.requireNonNull(owner);
    this.handle = Objects.requireNonNull(handle);
    this.durationTicks = Math.max(1, durationTicks);
    this.heldSlot = owner.getInventory().getHeldItemSlot();
    this.lastPulseMillis = startedAtMillis;
  }

  Player owner() {
    return owner;
  }

  GolemHandle handle() {
    return handle;
  }

  Mob golem() {
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
    return owner.getInventory().getHeldItemSlot() == heldSlot
        && GolemItems.kindOf(owner.getInventory().getItemInMainHand()).orElse(null) == GolemItemKind.CROOK;
  }

  boolean withinReach(double reach) {
    Location golemAt = golem().getLocation();
    Location ownerAt = owner.getLocation();
    return Objects.equals(golemAt.getWorld(), ownerAt.getWorld()) && golemAt.distanceSquared(ownerAt) <= reach * reach;
  }
}
