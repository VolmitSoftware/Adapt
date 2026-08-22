package art.arcane.adapt.util.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigFileSupportPassiveLoadTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void passiveLoadDoesNotCanonicalizeExistingConfig() throws IOException {
    Path configPath = temporaryDirectory.resolve("config.toml");
    String raw = "# preserve external editor bytes\ncount = 7\nenabled = true\n";
    Files.writeString(configPath, raw);

    TestConfig loaded = ConfigFileSupport.load(
        configPath.toFile(),
        null,
        TestConfig.class,
        new TestConfig(),
        false,
        "test-hotload",
        "unused",
        null,
        true
    );

    assertThat(loaded.count).isEqualTo(7);
    assertThat(loaded.enabled).isTrue();
    assertThat(Files.readString(configPath)).isEqualTo(raw);
  }

  @Test
  void passiveLoadDoesNotCreateMissingConfig() {
    Path configPath = temporaryDirectory.resolve("missing.toml");

    assertThatThrownBy(() -> ConfigFileSupport.load(
        configPath.toFile(),
        null,
        TestConfig.class,
        new TestConfig(),
        false,
        "test-hotload",
        "unused"
    )).isInstanceOf(IOException.class)
        .hasMessageContaining("missing");
    assertThat(configPath).doesNotExist();
  }

  @Test
  void passiveLoadReadsLegacyConfigWithoutMigratingIt() throws IOException {
    Path configPath = temporaryDirectory.resolve("config.toml");
    Path legacyPath = temporaryDirectory.resolve("config.json");
    Files.writeString(legacyPath, "{\"enabled\":true,\"count\":9}");

    TestConfig loaded = ConfigFileSupport.load(
        configPath.toFile(),
        legacyPath.toFile(),
        TestConfig.class,
        new TestConfig(),
        false,
        "test-hotload",
        "unused"
    );

    assertThat(loaded.count).isEqualTo(9);
    assertThat(loaded.enabled).isTrue();
    assertThat(configPath).doesNotExist();
    assertThat(legacyPath).exists();
  }

  @Test
  void snapshotParseUsesCapturedContentWithoutConsultingDisk() throws IOException {
    Path configPath = temporaryDirectory.resolve("config.toml");
    String diskContent = "enabled = false\ncount = 3\n";
    Files.writeString(configPath, diskContent);

    TestConfig loaded = ConfigFileSupport.parseSnapshot(
        "enabled = true\ncount = 11\n",
        configPath.toFile(),
        TestConfig.class,
        "test-hotload",
        null
    );

    assertThat(loaded.enabled).isTrue();
    assertThat(loaded.count).isEqualTo(11);
    assertThat(Files.readString(configPath)).isEqualTo(diskContent);
  }

  public static final class TestConfig {
    public boolean enabled;
    public int count;

    public TestConfig() {
    }
  }
}
