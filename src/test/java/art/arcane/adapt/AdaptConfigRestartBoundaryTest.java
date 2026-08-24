package art.arcane.adapt;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptConfigRestartBoundaryTest {
  @Test
  void reloadKeepsPersistenceAndMetricsSettingsFromStartup() throws Exception {
    AdaptConfig previous = new AdaptConfig();
    AdaptConfig loaded = new AdaptConfig();
    Field metrics = AdaptConfig.class.getDeclaredField("metrics");
    metrics.setAccessible(true);
    metrics.setBoolean(previous, false);
    metrics.setBoolean(loaded, true);

    AdaptConfig preserved = AdaptConfig.preserveRestartBoundSettings(previous, loaded);

    assertThat(preserved).isSameAs(loaded);
    assertThat(preserved.getSql()).isSameAs(previous.getSql());
    assertThat(preserved.getRedis()).isSameAs(previous.getRedis());
    assertThat(preserved.isMetrics()).isFalse();
  }
}
