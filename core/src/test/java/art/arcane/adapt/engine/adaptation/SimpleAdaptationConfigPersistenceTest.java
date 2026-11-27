package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleAdaptationConfigPersistenceTest {
  @TempDir
  Path temporaryDirectory;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(temporaryDirectory).build(), List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  private static TestAdaptation build() {
    return TestContent.adaptation(Id.adapt("normalized"), TestAdaptation::new);
  }

  private static Path configPath() {
    return ComponentHost.of(build()).getConfigFile().toPath();
  }

  @Test
  void optedInNormalizationRewritesExistingConfigBeforeApplyingRuntimeValues() throws IOException {
    Path configPath = configPath();
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, """
        enabled = true
        maxLevel = 5
        retainedValue = 9
        retiredCooldown = 60
        """);
    TestAdaptation adaptation = build();

    assertThat(ComponentHost.of(adaptation).reloadConfigFromDisk(false)).isTrue();

    String canonical = Files.readString(configPath);
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getConfig().maxLevel).isEqualTo(1);
    assertThat(adaptation.getConfig().retainedValue).isEqualTo(9);
    assertThat(canonical).contains("maxLevel = 1", "retainedValue = 9");
    assertThat(canonical).doesNotContain("retiredCooldown");

    assertThat(ComponentHost.of(adaptation).reloadConfigFromDisk(false)).isTrue();
    assertThat(Files.readString(configPath)).isEqualTo(canonical);
  }

  @Test
  void freshStartupLeavesCanonicalConfigBytesAndModificationTimeUntouched() throws IOException {
    TestAdaptation initial = build();
    Path configPath = ComponentHost.of(initial).getConfigFile().toPath();
    assertThat(ComponentHost.of(initial).reloadConfigFromDisk(false)).isTrue();
    String canonical = Files.readString(configPath);
    Files.setLastModifiedTime(configPath, FileTime.fromMillis(1_000L));
    FileTime modified = Files.getLastModifiedTime(configPath);

    for (int startup = 0; startup < 3; startup++) {
      TestAdaptation restarted = build();
      assertThat(ComponentHost.of(restarted).reloadConfigFromDisk(false)).isTrue();
      assertThat(Files.readString(configPath)).isEqualTo(canonical);
      assertThat(Files.getLastModifiedTime(configPath)).isEqualTo(modified);
    }
  }

  @Test
  void snapshotReloadAppliesCapturedContentWithoutRereadingOrRewritingDisk() throws IOException {
    Path configPath = configPath();
    Files.createDirectories(configPath.getParent());
    String diskContent = """
        enabled = true
        maxLevel = 8
        retainedValue = 4
        """;
    Files.writeString(configPath, diskContent);
    TestAdaptation adaptation = build();

    boolean reloaded = ComponentHost.of(adaptation).reloadConfigSnapshot("""
        enabled = true
        maxLevel = 5
        retainedValue = 17
        """, configPath.toFile(), false);

    assertThat(reloaded).isTrue();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getConfig().retainedValue).isEqualTo(17);
    assertThat(Files.readString(configPath)).isEqualTo(diskContent);
  }

  public static final class TestAdaptation extends SimpleAdaptation<TestConfig> {
    private static final List<PlayerPreference<?>> PREFERENCES = List.of(
        CommonPreferences.ENABLED,
        CommonPreferences.toggle("particles", Text.literal("Particles")),
        CommonPreferences.toggle("activation", Text.literal("Activation"))
    );
    private TestAdaptation() {
      super(Id.adapt("normalized/adaptation"));
      registerConfiguration(TestConfig.class);
    }

    @Override
    public List<PlayerPreference<?>> getPlayerPreferences() {
      return PREFERENCES;
    }

    @Override
    protected void normalizeLoadedConfig(TestConfig loadedConfig) {
      loadedConfig.maxLevel = 1;
    }

    @Override
    public void addStats(int level, StatLines element) {
    }
  }

  public static final class TestConfig extends AdaptationConfig {
    int retainedValue = 3;
  }
}
