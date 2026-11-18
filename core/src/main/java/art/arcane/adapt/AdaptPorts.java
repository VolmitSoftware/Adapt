package art.arcane.adapt;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed registry for collaborators that only one platform can provide, such as third-party plugin integrations,
 * Redis transfer or PlaceholderAPI publishing. Each port is a small core interface with a neutral fallback; a
 * platform installs its implementation before {@link AdaptCore#start()} and core code reads it with
 * {@link #get(Class, Object)}, passing the fallback.
 */
public final class AdaptPorts {
  private static final Map<Class<?>, Object> INSTALLED = new ConcurrentHashMap<>();

  private AdaptPorts() {
  }

  public static <T> void install(Class<T> type, T implementation) {
    INSTALLED.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(implementation, "implementation"));
  }

  public static <T> void uninstall(Class<T> type) {
    INSTALLED.remove(type);
  }

  public static <T> T get(Class<T> type, T fallback) {
    Object installed = INSTALLED.get(type);
    return installed == null ? fallback : type.cast(installed);
  }

  public static boolean isInstalled(Class<?> type) {
    return INSTALLED.containsKey(type);
  }

  public static void clear() {
    INSTALLED.clear();
  }
}
