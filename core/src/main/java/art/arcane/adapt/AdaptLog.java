package art.arcane.adapt;

import java.util.function.Supplier;

/**
 * Adapt's log facade for platform-neutral code, with the same methods the Bukkit plugin class offers. Debug and
 * verbose messages are built only when the matching config switch is on.
 */
public final class AdaptLog {
  private static final String DEBUG_COLOR = "§5";
  private static final String SUCCESS_COLOR = "§a";

  private AdaptLog() {
  }

  public static void debug(String message) {
    debug(() -> message);
  }

  public static void debug(Supplier<String> message) {
    if (isDebug()) {
      AdaptEnvironment.log().info(DEBUG_COLOR + message.get());
    }
  }

  public static void verbose(String message) {
    verbose(() -> message);
  }

  public static void verbose(Supplier<String> message) {
    if (isVerbose()) {
      AdaptEnvironment.log().verbose(message.get());
    }
  }

  public static void info(String message) {
    AdaptEnvironment.log().info(message);
  }

  public static void msg(String message) {
    AdaptEnvironment.log().info(message);
  }

  public static void success(String message) {
    AdaptEnvironment.log().info(SUCCESS_COLOR + message);
  }

  public static void warn(String message) {
    AdaptEnvironment.log().warn(message);
  }

  public static void warn(String message, Throwable error) {
    AdaptEnvironment.log().warn(message);
    if (error != null) {
      AdaptEnvironment.log().error(describe(error), error);
    }
  }

  public static void error(String message) {
    AdaptEnvironment.log().error(message, null);
  }

  public static void error(String message, Throwable error) {
    AdaptEnvironment.log().error(message, error);
  }

  public static void error(Throwable error) {
    AdaptEnvironment.log().error(describe(error), error);
  }

  private static String describe(Throwable error) {
    return error == null || error.getMessage() == null || error.getMessage().isBlank()
        ? "Unhandled Adapt failure"
        : error.getMessage();
  }

  private static boolean isDebug() {
    try {
      return AdaptConfig.get().isDebug();
    } catch (RuntimeException | LinkageError unavailable) {
      return false;
    }
  }

  private static boolean isVerbose() {
    try {
      return AdaptConfig.get().isVerbose();
    } catch (RuntimeException | LinkageError unavailable) {
      return false;
    }
  }
}
