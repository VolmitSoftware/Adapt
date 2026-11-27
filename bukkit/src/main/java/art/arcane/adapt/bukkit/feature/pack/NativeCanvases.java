package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.canvas.Canvases;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads the server canvas implementation on first use. Any linkage or reflection failure, at load or later, turns
 * canvases off for the rest of the run; pack windows then use plain chests.
 */
final class NativeCanvases {
  private final Logger logger;
  private volatile Canvases canvases;
  private volatile boolean disabled;

  NativeCanvases(Logger logger) {
    this.logger = logger;
  }

  /** The canvases, or null when this server cannot run them. */
  Canvases get(Player sample) {
    Canvases loaded = canvases;
    if (loaded != null || disabled) {
      return loaded;
    }
    synchronized (this) {
      if (canvases == null && !disabled) {
        try {
          Canvases created = (Canvases) Class.forName(Canvases.IMPLEMENTATION, true, NativeCanvases.class.getClassLoader())
              .getConstructor()
              .newInstance();
          created.verify(sample);
          canvases = created;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
          disable(failure);
        }
      }
      return canvases;
    }
  }

  void disable(Throwable failure) {
    synchronized (this) {
      if (disabled) {
        return;
      }
      disabled = true;
      canvases = null;
    }
    logger.log(Level.WARNING, "Pack canvas windows are not available on this server build; pack menus use plain chests", failure);
  }
}
