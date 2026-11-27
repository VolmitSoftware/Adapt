package art.arcane.adapt.bukkit.feature.pack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NativeCanvasesTest {
  @Test
  void aServerWithoutCanvasSupportFallsBackOnceAndStaysOff() {
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    List<LogRecord> records = new ArrayList<>();
    logger.addHandler(new Handler() {
      @Override
      public void publish(LogRecord record) {
        records.add(record);
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    });
    NativeCanvases canvases = new NativeCanvases(logger);

    assertThat(canvases.get(mock(Player.class))).isNull();
    assertThat(canvases.get(mock(Player.class))).isNull();
    assertThat(records).hasSize(1);
  }
}
