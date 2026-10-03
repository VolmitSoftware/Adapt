package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

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

  @Test
  void freshStartupLeavesCanonicalConfigBytesAndModificationTimeUntouched() throws IOException {
    Path configPath = temporaryDirectory.resolve("normalized-adaptation.toml");
    TestAdaptation initial = new TestAdaptation(configPath);
    assertThat(initial.reloadConfigFromDisk(false)).isTrue();
    String canonical = Files.readString(configPath);
    Files.setLastModifiedTime(configPath, FileTime.fromMillis(1_000L));
    FileTime modified = Files.getLastModifiedTime(configPath);

    for (int startup = 0; startup < 3; startup++) {
      TestAdaptation restarted = new TestAdaptation(configPath);
      assertThat(restarted.reloadConfigFromDisk(false)).isTrue();
      assertThat(Files.readString(configPath)).isEqualTo(canonical);
      assertThat(Files.getLastModifiedTime(configPath)).isEqualTo(modified);
    }
  }

  @Test
  void snapshotReloadAppliesCapturedContentWithoutRereadingOrRewritingDisk() throws IOException {
    Path configPath = temporaryDirectory.resolve("normalized-adaptation.toml");
    String diskContent = """
        enabled = true
        maxLevel = 8
        retainedValue = 4
        """;
    Files.writeString(configPath, diskContent);
    TestAdaptation adaptation = new TestAdaptation(configPath);

    boolean reloaded = adaptation.reloadConfigSnapshot("""
        enabled = true
        maxLevel = 5
        retainedValue = 17
        """, configPath.toFile(), false);

    assertThat(reloaded).isTrue();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getConfig().retainedValue).isEqualTo(17);
    assertThat(Files.readString(configPath)).isEqualTo(diskContent);
  }

  private static final class TestAdaptation extends SimpleAdaptation<TestConfig> {
    private static final List<PlayerPreference<?>> PREFERENCES = List.of(
        CommonPreferences.ENABLED,
        CommonPreferences.toggle("particles", TextKey.of("test.particles", "Particles")),
        CommonPreferences.toggle("activation", TextKey.of("test.activation", "Activation"))
    );
    private final File configFile;

    private TestAdaptation(Path configPath) {
      super("normalized-adaptation");
      configFile = configPath.toFile();
      registerConfiguration(TestConfig.class);
    }

    @Override
    public List<PlayerPreference<?>> getPlayerPreferences() {
      return PREFERENCES;
    }

    @Override
    protected File getConfigFile() {
      return configFile;
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
