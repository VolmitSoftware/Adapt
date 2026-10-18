package art.arcane.adapt.api.golem;

import org.bukkit.attribute.AttributeInstance;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemColossus {
  private static final AtomicReference<Form> FORM = new AtomicReference<>();

  private GolemColossus() {
  }

  public static void register(Form form) {
    FORM.set(Objects.requireNonNull(form));
  }

  public static boolean unregister(Form form) {
    return FORM.compareAndSet(form, null);
  }

  public static Optional<Form> form() {
    return Optional.ofNullable(FORM.get());
  }

  public static void settle(GolemHandle handle) {
    if (handle.isRetired() || !handle.state().colossus()) {
      return;
    }
    Form current = FORM.get();
    if (current == null) {
      revert(handle);
      return;
    }
    if (handle.platform().ownerReady(handle.owner())) {
      current.settle(handle);
    }
  }

  private static void revert(GolemHandle handle) {
    AttributeInstance scale = handle.platform().scaleOf(handle.entity());
    if (scale != null) {
      double bound = GolemScale.target(handle.body());
      GolemScale.stepTo(scale, bound, bound, 1D);
    }
    handle.setColossus(false);
  }

  public enum Toggle {
    UNAVAILABLE,
    BUSY,
    NO_ROOM,
    GROW,
    SHRINK
  }

  public interface Form {
    boolean available(GolemHandle handle);

    Toggle toggle(GolemHandle handle);

    void settle(GolemHandle handle);
  }
}
