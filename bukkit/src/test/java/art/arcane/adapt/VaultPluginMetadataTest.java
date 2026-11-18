package art.arcane.adapt;

import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class VaultPluginMetadataTest {
  @Test
  void vaultIsAnOptionalDependency() {
    assertThatNoException().isThrownBy(() -> {
      try (InputStream stream = VaultPluginMetadataTest.class.getResourceAsStream("/plugin.yml")) {
        assertThat(stream).isNotNull();
        PluginDescriptionFile metadata = new PluginDescriptionFile(stream);
        assertThat(metadata.getSoftDepend()).contains("Vault");
      }
    });
  }
}
