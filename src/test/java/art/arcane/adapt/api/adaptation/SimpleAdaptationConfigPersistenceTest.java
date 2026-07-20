package art.arcane.adapt.api.adaptation;

import art.arcane.volmlib.util.inventorygui.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleAdaptationConfigPersistenceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void optedInNormalizationRewritesExistingConfigBeforeApplyingRuntimeValues() throws IOException {
    Path configPath = temporaryDirectory.resolve("normalized-adaptation.toml");
    Files.writeString(configPath, """
        enabled = true
        maxLevel = 5
        retainedValue = 9
        retiredCooldown = 60
        """);
    TestAdaptation adaptation = new TestAdaptation(configPath);

    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();

    String canonical = Files.readString(configPath);
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getConfig().maxLevel).isEqualTo(1);
    assertThat(adaptation.getConfig().retainedValue).isEqualTo(9);
    assertThat(canonical).contains("maxLevel = 1", "retainedValue = 9");
    assertThat(canonical).doesNotContain("retiredCooldown");

    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(Files.readString(configPath)).isEqualTo(canonical);
  }

  private static final class TestAdaptation extends SimpleAdaptation<TestConfig> {
    private final File configFile;
    private final File legacyConfigFile;

    private TestAdaptation(Path configPath) {
      super("normalized-adaptation");
      configFile = configPath.toFile();
      legacyConfigFile = configPath.resolveSibling("normalized-adaptation.json").toFile();
      registerConfiguration(TestConfig.class);
    }

    @Override
    protected File getConfigFile() {
      return configFile;
    }

    @Override
    protected File getLegacyConfigFile() {
      return legacyConfigFile;
    }

    @Override
    protected TestConfig createDefaultConfig() {
      return new TestConfig();
    }

    @Override
    protected void normalizeLoadedConfig(TestConfig loadedConfig) {
      loadedConfig.maxLevel = 1;
    }

    @Override
    protected boolean shouldCanonicalizeConfigOnLoad() {
      return true;
    }

    @Override
    public void addStats(int level, Element element) {
    }
  }

  private static final class TestConfig extends AdaptationConfig {
    private int retainedValue = 3;
  }
}
