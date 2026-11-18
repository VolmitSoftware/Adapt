package art.arcane.adapt;

import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalIntegrationMetadataTest {
  @Test
  void directOptionalIntegrationsHaveDeterministicLoadOrder() throws Exception {
    try (InputStream stream = OptionalIntegrationMetadataTest.class.getResourceAsStream("/plugin.yml")) {
      assertThat(stream).isNotNull();
      PluginDescriptionFile metadata = new PluginDescriptionFile(stream);
      assertThat(metadata.getSoftDepend()).contains(
          "AdvancedChests",
          "MagicCosmetics",
          "GriefDefender",
          "GriefPrevention",
          "LockettePro"
      );
    }
  }
}
