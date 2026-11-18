package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.platform.PlatformLog;

/** Routes platform logging through the plugin's logger and verbose switch. */
public final class BukkitLog implements PlatformLog {
  @Override
  public void info(String message) {
    Adapt.info(message);
  }

  @Override
  public void warn(String message) {
    Adapt.warn(message);
  }

  @Override
  public void error(String message, Throwable error) {
    if (error == null) {
      Adapt.error(message);
    } else {
      Adapt.error(message, error);
    }
  }

  @Override
  public void verbose(String message) {
    Adapt.verbose(message);
  }
}
