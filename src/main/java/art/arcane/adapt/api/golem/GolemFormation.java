package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import org.bukkit.Location;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemFormation {
  private static final AtomicReference<Formation> FORMATION = new AtomicReference<>();
  private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

  private GolemFormation() {
  }

  public static void register(Formation formation) {
    FORMATION.set(Objects.requireNonNull(formation));
    FAILURE_LOGGED.set(false);
  }

  public static boolean unregister(Formation formation) {
    return FORMATION.compareAndSet(formation, null);
  }

  public static Location slot(GolemHandle handle, Location ownerAt) {
    Formation current = FORMATION.get();
    if (current == null || ownerAt == null) {
      return null;
    }
    try {
      return current.slot(handle, ownerAt);
    } catch (RuntimeException failure) {
      if (FAILURE_LOGGED.compareAndSet(false, true)) {
        Adapt.warn("Formation slot lookup failed for bound golem " + handle.id() + "; it follows its owner directly.", failure);
      }
      return null;
    }
  }

  public interface Formation {
    Location slot(GolemHandle handle, Location ownerAt);
  }
}
