package art.arcane.adapt.util.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigMigrationManagerTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void retiredConfigsAreDeletedWithoutTouchingActiveConfigs() throws IOException {
    Path adaptationsDirectory = temporaryDirectory.resolve("adapt").resolve("adaptations");
    Files.createDirectories(adaptationsDirectory);
    Files.writeString(adaptationsDirectory.resolve("axe-orchardist.toml"), "enabled = true");
    Files.writeString(adaptationsDirectory.resolve("axe-orchardist.json"), "{}");
    Files.writeString(adaptationsDirectory.resolve("axe-sap-tap.toml"), "enabled = true");
    Files.writeString(adaptationsDirectory.resolve("axe-sap-tap.json"), "{}");
    Files.writeString(adaptationsDirectory.resolve("axe-timber-mark.toml"), "enabled = true");
    Files.writeString(adaptationsDirectory.resolve("axe-timber-mark.json"), "{}");
    Files.writeString(adaptationsDirectory.resolve("excavation-dowsing.toml"), "enabled = true");
    Files.writeString(adaptationsDirectory.resolve("excavation-dowsing.json"), "{}");
    Files.writeString(adaptationsDirectory.resolve("rift-step.toml"), "enabled = true");
    Files.writeString(adaptationsDirectory.resolve("rift-step.json"), "{}");
    Path activeConfig = adaptationsDirectory.resolve("axe-shield-splitter.toml");
    Files.writeString(activeConfig, "enabled = true");

    int deleted = ConfigMigrationManager.deleteRetiredAdaptationConfigs(temporaryDirectory.toFile());

    assertThat(deleted).isEqualTo(10);
    assertThat(activeConfig).exists();
    assertThat(adaptationsDirectory.toFile().listFiles())
        .extracting(File::getName)
        .containsExactly("axe-shield-splitter.toml");
  }
}
