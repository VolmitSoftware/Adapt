package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.PlatformLog;
import org.jetbrains.annotations.ApiStatus;

import java.util.logging.Level;
import java.util.logging.Logger;

/** The log {@code AdaptApi.log()} returns: the running core's, or {@code java.util.logging} without one. */
@ApiStatus.Internal
final class CoreLog implements PlatformLog {
  static final CoreLog INSTANCE = new CoreLog();
  private static final Logger FALLBACK = Logger.getLogger("Adapt");

  private CoreLog() {
  }

  @Override
  public void info(String message) {
    EngineBridge bridge = Engine.current();
    if (bridge == null) {
      FALLBACK.info(message);
    } else {
      bridge.log().info(message);
    }
  }

  @Override
  public void warn(String message) {
    EngineBridge bridge = Engine.current();
    if (bridge == null) {
      FALLBACK.warning(message);
    } else {
      bridge.log().warn(message);
    }
  }

  @Override
  public void error(String message, Throwable error) {
    EngineBridge bridge = Engine.current();
    if (bridge == null) {
      FALLBACK.log(Level.SEVERE, message, error);
    } else {
      bridge.log().error(message, error);
    }
  }

  @Override
  public void verbose(String message) {
    EngineBridge bridge = Engine.current();
    if (bridge == null) {
      FALLBACK.fine(message);
    } else {
      bridge.log().verbose(message);
    }
  }
}
