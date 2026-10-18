package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import org.bukkit.Location;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemMend {
  private static final AtomicReference<Mender> MENDER = new AtomicReference<>();

  private GolemMend() {
  }

  public static void register(Mender mender) {
    MENDER.set(Objects.requireNonNull(mender));
  }

  public static boolean unregister(Mender mender) {
    return MENDER.compareAndSet(mender, null);
  }

  public static Optional<Mender> mender() {
    return Optional.ofNullable(MENDER.get());
  }

  static Mender active() {
    return MENDER.get();
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

  public interface Mender {
    long regenIntervalMillis();

    double regen(GolemHandle handle, RegenSenses senses);

    boolean shatters(GolemHandle handle);

    void shattered(GolemHandle handle, Location at);
  }

  public record RegenSenses(double ownerDistance, double homeDistance, boolean idle) {
  }
}
