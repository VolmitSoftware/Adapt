package art.arcane.adapt;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

class AdaptLoggingTest {
  private Adapt previousInstance;
  private Logger logger;
  private RecordingHandler handler;

  @BeforeEach
  void setUp() {
    previousInstance = Adapt.instance;
    logger = Logger.getLogger("AdaptLoggingTest-" + UUID.randomUUID());
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
    handler = new RecordingHandler();
    handler.setLevel(Level.ALL);
    logger.addHandler(handler);

    Adapt plugin = Mockito.mock(Adapt.class);
    Mockito.when(plugin.getLogger()).thenReturn(logger);
    Adapt.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    logger.removeHandler(handler);
    Adapt.instance = previousInstance;
  }

  @Test
  void julFallbackRetainsSeverityWithoutLegacySymbols() {
    try (MockedStatic<ComponentLogger> componentLogger = Mockito.mockStatic(ComponentLogger.class)) {
      componentLogger.when(ComponentLogger::logger).thenReturn(null);
      Adapt.info("information");
      Adapt.warn("warning");
      Adapt.error("failure");
    }

    List<LogRecord> records = handler.records();
    Assertions.assertEquals(3, records.size());
    Assertions.assertEquals(Level.INFO, records.get(0).getLevel());
    Assertions.assertEquals(Level.WARNING, records.get(1).getLevel());
    Assertions.assertEquals(Level.SEVERE, records.get(2).getLevel());
    Assertions.assertEquals("information", records.get(0).getMessage());
    Assertions.assertEquals("warning", records.get(1).getMessage());
    Assertions.assertEquals("failure", records.get(2).getMessage());
    Assertions.assertTrue(records.stream().noneMatch(record -> record.getMessage().contains("\u00a7")));
  }

  @Test
  void julFallbackRetainsFailureCause() {
    IllegalStateException failure = new IllegalStateException("broken");

    try (MockedStatic<ComponentLogger> componentLogger = Mockito.mockStatic(ComponentLogger.class)) {
      componentLogger.when(ComponentLogger::logger).thenReturn(null);
      Adapt.error("Contextual failure", failure);
    }

    Assertions.assertEquals(1, handler.records().size());
    Assertions.assertSame(failure, handler.records().get(0).getThrown());
  }

  private static final class RecordingHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      if (record != null && isLoggable(record)) {
        records.add(record);
      }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    private List<LogRecord> records() {
      return List.copyOf(records);
    }
  }
}
