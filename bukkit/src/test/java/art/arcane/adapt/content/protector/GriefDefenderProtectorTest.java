package art.arcane.adapt.content.protector;

import art.arcane.adapt.AdaptConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GriefDefenderProtectorTest {
  @Test
  void defaultEnablementUsesTheGriefDefenderSetting() {
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.Protector settings = mock(AdaptConfig.Protector.class);
    when(config.getProtectorSupport()).thenReturn(settings);
    when(settings.isGriefdefender()).thenReturn(true);
    when(settings.isFactionsClaim()).thenReturn(false);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);

      assertThat(new GriefDefenderProtector().isEnabledByDefault()).isTrue();
    }
  }
}
