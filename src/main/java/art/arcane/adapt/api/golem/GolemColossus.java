package art.arcane.adapt.api.golem;

import org.bukkit.attribute.AttributeInstance;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class GolemColossus {
  public static final int RECONCILE_DELAY_TICKS = 20;
  private static final AtomicReference<Form> FORM = new AtomicReference<>();

  private GolemColossus() {
  }

  public static void register(Form form) {
    FORM.set(Objects.requireNonNull(form));
  }

  public static boolean unregister(Form form) {
    if (!FORM.compareAndSet(form, null)) {
      return false;
    }
    GolemancyRuntime.registry().ifPresent(GolemColossus::reconcileLater);
    return true;
  }

  public static Optional<Form> form() {
    return Optional.ofNullable(FORM.get());
  }

  public static void settle(GolemHandle handle) {
    Form current = FORM.get();
    if (current != null && !handle.isRetired() && handle.state().colossus() && handle.platform().ownerReady(handle.owner())) {
      current.settle(handle);
    }
  }

  public static void reconcile(GolemHandle handle) {
    if (handle.isRetired() || !handle.state().colossus()) {
      return;
    }
    if (FORM.get() == null) {
      revert(handle);
      return;
    }
    settle(handle);
  }

  private static void reconcileLater(GolemRegistry registry) {
    for (GolemHandle handle : registry.loaded()) {
      if (!handle.isDormant() && handle.state().colossus()) {
        handle.platform().runEntity(handle.entity(), () -> reconcile(handle), RECONCILE_DELAY_TICKS);
      }
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
