package art.arcane.adapt.api.golem;

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

  public static void unregister(Form form) {
    FORM.compareAndSet(form, null);
  }

  public static Optional<Form> form() {
    return Optional.ofNullable(FORM.get());
  }

  public interface Form {
    boolean available(GolemHandle handle);

    void toggle(GolemHandle handle);
  }
}
