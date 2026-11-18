package art.arcane.adapt;

import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HiddenOreIntegrationTest {
  @Test
  void disabledHiddenOreDoesNotEnableIntegration() {
    PluginManager pluginManager = mock(PluginManager.class);
    when(pluginManager.isPluginEnabled("HiddenOre")).thenReturn(false);

    assertThat(Adapt.isHiddenOreIntegrationAvailable(pluginManager)).isFalse();
  }

  @Test
  void enabledHiddenOreEnablesIntegration() {
    PluginManager pluginManager = mock(PluginManager.class);
    when(pluginManager.isPluginEnabled("HiddenOre")).thenReturn(true);

    assertThat(Adapt.isHiddenOreIntegrationAvailable(pluginManager)).isTrue();
  }
}
