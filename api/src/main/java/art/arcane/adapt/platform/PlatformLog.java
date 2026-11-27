package art.arcane.adapt.platform;

import org.jetbrains.annotations.ApiStatus;

/** Platform logger. Thread-safe. */
@ApiStatus.AvailableSince("1.0.0")
public interface PlatformLog {
  /** Logs at info level. */
  void info(String message);

  /** Logs at warning level. */
  void warn(String message);

  /** Logs at error level together with {@code error}, which may be null. */
  void error(String message, Throwable error);

  /** Logged only when Adapt's verbose mode is on. */
  void verbose(String message);
}
