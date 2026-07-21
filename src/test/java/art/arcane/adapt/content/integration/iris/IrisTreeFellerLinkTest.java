package art.arcane.adapt.content.integration.iris;

import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IrisTreeFellerLinkTest {
  @Test
  void disabledIrisHidesTheIntegration() {
    PluginManager pluginManager = mock(PluginManager.class);
    when(pluginManager.isPluginEnabled("Iris")).thenReturn(false);

    assertThat(IrisTreeFellerLink.isAvailable(pluginManager)).isFalse();
  }

  @Test
  void enabledIrisExposesTheIntegration() {
    PluginManager pluginManager = mock(PluginManager.class);
    when(pluginManager.isPluginEnabled("Iris")).thenReturn(true);

    assertThat(IrisTreeFellerLink.isAvailable(pluginManager)).isTrue();
  }

  @Test
  void optionalLinkKeepsIrisProviderTypesInsideTheBridge() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/integration/iris/IrisTreeFellerLink.java"));

    assertThat(source).doesNotContain("art.arcane.iris.api");
  }
}
