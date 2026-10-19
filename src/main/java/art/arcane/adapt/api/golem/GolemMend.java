package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import org.bukkit.Location;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemMend {
  private static final AtomicReference<Mender> MENDER = new AtomicReference<>();
  private static final AtomicBoolean REGEN_FAILURE_LOGGED = new AtomicBoolean();

  private GolemMend() {
  }

  public static void register(Mender mender) {
    MENDER.set(Objects.requireNonNull(mender));
    REGEN_FAILURE_LOGGED.set(false);
  }

  public static boolean unregister(Mender mender) {
    return MENDER.compareAndSet(mender, null);
  }

  static long regenIntervalMillis() {
    Mender current = MENDER.get();
    if (current == null) {
      return 0L;
    }
    try {
      return Math.max(1L, current.regenIntervalMillis());
    } catch (RuntimeException failure) {
      logRegenFailure(failure);
      return 0L;
    }
  }

  static double regen(GolemHandle handle, RegenSenses senses) {
    Mender current = MENDER.get();
    if (current == null) {
      return 0D;
    }
    try {
      double amount = current.regen(handle, senses);
      return Double.isFinite(amount) ? Math.max(0D, amount) : 0D;
    } catch (RuntimeException failure) {
      logRegenFailure(failure);
      return 0D;
    }
  }

  static boolean shatters(GolemHandle handle) {
    Mender current = MENDER.get();
    if (current == null) {
      return false;
    }
    try {
      return current.shatters(handle);
    } catch (RuntimeException failure) {
      Adapt.warn("Golem Mending could not decide whether golem " + handle.id() + " shatters; it dies as usual.", failure);
      return false;
    }
  }

  static void shattered(GolemHandle handle, Location at) {
    Mender current = MENDER.get();
    if (current == null) {
      return;
    }
    try {
      current.shattered(handle, at);
    } catch (RuntimeException failure) {
      Adapt.warn("Golem Mending could not show that golem " + handle.id() + " shattered; its Shattered Core was dropped.", failure);
    }
  }

  private static void logRegenFailure(RuntimeException failure) {
    if (REGEN_FAILURE_LOGGED.compareAndSet(false, true)) {
      Adapt.warn("Golem Mending regeneration failed; golems skip regeneration while it keeps failing.", failure);
    }
  }

  public interface Mender {
    long regenIntervalMillis();

    double regen(GolemHandle handle, RegenSenses senses);

    boolean shatters(GolemHandle handle);

    void shattered(GolemHandle handle, Location at);
  }

  public record RegenSenses(double ownerDistance, double homeDistance, boolean idle) {
  }
}
