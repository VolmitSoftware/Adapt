package art.arcane.adapt;

import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.value.Id;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process-wide log sink, data directory and config binding for platform-neutral code. Each platform installs its own
 * before any Adapt code runs; until then messages go to {@code java.util.logging}, there is no data directory, and
 * config block names are only normalized.
 */
public final class AdaptEnvironment {
  private static final PlatformLog FALLBACK_LOG = new JdkLog(Logger.getLogger("Adapt"));
  private static final Supplier<Path> NO_DATA_DIRECTORY = () -> null;
  private static final AdaptConfig.Binding UNBOUND_CONFIG = new UnboundConfig();
  private static volatile PlatformLog log = FALLBACK_LOG;
  private static volatile Supplier<Path> dataDirectory = NO_DATA_DIRECTORY;
  private static volatile AdaptConfig.Binding configBinding = UNBOUND_CONFIG;

  private AdaptEnvironment() {
  }

  public static void install(PlatformLog platformLog, Supplier<Path> platformDataDirectory) {
    log = Objects.requireNonNull(platformLog, "platformLog");
    dataDirectory = Objects.requireNonNull(platformDataDirectory, "platformDataDirectory");
  }

  public static void bindConfig(AdaptConfig.Binding binding) {
    configBinding = Objects.requireNonNull(binding, "binding");
  }

  public static AdaptConfig.Binding configBinding() {
    return configBinding;
  }

  public static PlatformLog log() {
    return log;
  }

  /** Adapt's data directory, or null when no platform has installed one. */
  public static Path dataDirectory() {
    return dataDirectory.get();
  }

  /** The config file of a skill or adaptation: {@code <kind>/<namespace>/<path>.toml} under the data directory. */
  public static File contentConfigFile(String kind, Id id) {
    return dataFile(kind, id.namespace(), id.path().replace('/', File.separatorChar) + ".toml");
  }

  /** {@link #contentConfigFile}'s path relative to the data directory, with {@code /} separators. */
  public static String contentConfigPath(String kind, Id id) {
    return kind + "/" + id.namespace() + "/" + id.path() + ".toml";
  }

  /** The content id a {@link #contentConfigFile} path names, given its path below {@code <kind>/}; null otherwise. */
  public static Id contentIdOfConfig(String relativePath) {
    String normalized = relativePath.replace(File.separatorChar, '/');
    int slash = normalized.indexOf('/');
    if (slash <= 0 || !normalized.endsWith(".toml")) {
      return null;
    }
    String path = normalized.substring(slash + 1, normalized.length() - ".toml".length());
    return path.isEmpty() ? null : Id.tryParse(normalized.substring(0, slash) + ":" + path);
  }

  /** A file under the data directory; its parent directories are created. */
  public static File dataFile(String... path) {
    Path directory = dataDirectory();
    if (directory == null) {
      throw new IllegalStateException("Adapt data directory is not available");
    }
    File file = new File(directory.toFile(), String.join(File.separator, path));
    file.getParentFile().mkdirs();
    return file;
  }

  public static void info(String message) {
    log.info(message);
  }

  public static void warn(String message) {
    log.warn(message);
  }

  public static void error(String message) {
    log.error(message, null);
  }

  public static void error(String message, Throwable error) {
    log.error(message, error);
  }

  public static void error(Throwable error) {
    String message = error == null || error.getMessage() == null || error.getMessage().isBlank()
        ? "Unhandled Adapt failure"
        : error.getMessage();
    log.error(message, error);
  }

  public static void verbose(String message) {
    log.verbose(message);
  }

  private record JdkLog(Logger logger) implements PlatformLog {
    @Override
    public void info(String message) {
      logger.info(message);
    }

    @Override
    public void warn(String message) {
      logger.warning(message);
    }

    @Override
    public void error(String message, Throwable error) {
      logger.log(Level.SEVERE, message, error);
    }

    @Override
    public void verbose(String message) {
      logger.fine(message);
    }
  }

  private static final class UnboundConfig implements AdaptConfig.Binding {
    @Override
    public String blockName(String configured) {
      String name = configured.startsWith("minecraft:") ? configured.substring("minecraft:".length()) : configured;
      name = name.toUpperCase(Locale.ENGLISH).replaceAll("\\s+", "_").replaceAll("\\W", "");
      return name.isEmpty() ? null : name;
    }

    @Override
    public void reloaded() {
    }
  }
}
