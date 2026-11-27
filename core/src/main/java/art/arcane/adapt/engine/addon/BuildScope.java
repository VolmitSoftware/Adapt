package art.arcane.adapt.engine.addon;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The addon whose content the engine is building on this thread. Content is only built by the engine calling a
 * supplier, and a component's constructor asks this scope which addon it belongs to.
 */
public final class BuildScope {
  private static final ThreadLocal<String> OWNER = new ThreadLocal<>();

  private BuildScope() {
  }

  /** Runs {@code build} as content of {@code owner}; scopes nest. */
  public static <T> T run(String owner, Supplier<T> build) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(build, "build");
    String previous = OWNER.get();
    OWNER.set(owner);
    try {
      return build.get();
    } finally {
      if (previous == null) {
        OWNER.remove();
      } else {
        OWNER.set(previous);
      }
    }
  }

  /** As {@link #run(String, Supplier)}, rethrowing a failure naming {@code what} was built, such as {@code skill a:b}. */
  public static <T> T run(String owner, String what, Supplier<T> build) {
    Objects.requireNonNull(what, "what");
    try {
      return run(owner, build);
    } catch (RuntimeException failure) {
      String reason = failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
      throw new IllegalStateException("Building " + what + " failed: " + reason, failure);
    }
  }

  /** The namespace of the addon being built; throws outside {@link #run}. */
  public static String owner() {
    String owner = OWNER.get();
    if (owner == null) {
      throw new IllegalStateException("Content is built by Adapt from the suppliers an addon registers; "
          + "it cannot be constructed directly.");
    }
    return owner;
  }

  public static boolean active() {
    return OWNER.get() != null;
  }
}
