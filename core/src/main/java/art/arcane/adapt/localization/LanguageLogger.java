package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptEnvironment;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** A {@code java.util.logging} logger for VolmLib localization services that writes to Adapt's platform log. */
final class LanguageLogger {
  private static final Logger LOGGER = create();

  private LanguageLogger() {
  }

  static Logger get() {
    return LOGGER;
  }

  private static Logger create() {
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
    logger.addHandler(new PlatformHandler());
    return logger;
  }

  private static final class PlatformHandler extends Handler {
    private final SimpleFormatter formatter = new SimpleFormatter();

    @Override
    public void publish(LogRecord record) {
      if (record == null) {
        return;
      }
      String message = formatter.formatMessage(record);
      int level = record.getLevel().intValue();
      if (level >= Level.SEVERE.intValue()) {
        AdaptEnvironment.log().error(message, record.getThrown());
      } else if (level >= Level.WARNING.intValue()) {
        AdaptEnvironment.log().warn(message);
        if (record.getThrown() != null) {
          AdaptEnvironment.log().error(message, record.getThrown());
        }
      } else if (level >= Level.INFO.intValue()) {
        AdaptEnvironment.log().info(message);
      } else {
        AdaptEnvironment.log().verbose(message);
      }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  }
}
