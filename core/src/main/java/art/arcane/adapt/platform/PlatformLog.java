package art.arcane.adapt.platform;

/** Platform logger. Thread-safe. */
public interface PlatformLog {
  void info(String message);

  void warn(String message);

  void error(String message, Throwable error);

  /** Logged only when Adapt's verbose mode is on. */
  void verbose(String message);
}
