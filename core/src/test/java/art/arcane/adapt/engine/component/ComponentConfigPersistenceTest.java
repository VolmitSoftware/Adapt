package art.arcane.adapt.engine.component;

import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.platform.value.Id;
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

import static org.assertj.core.api.Assertions.assertThat;

/** A component's config is normalized and rewritten in canonical form when read from disk, and not when hotloaded. */
class ComponentConfigPersistenceTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void readingFromDiskNormalizesAndRewritesTheConfig() throws IOException {
    NormalizedSkill skill = TestContent.skill(NormalizedSkill::new);
    ComponentHost host = ComponentHost.of(skill);
    Path configPath = host.getConfigFile().toPath();
    Files.writeString(configPath, """
        enabled = true
        reward = 45
        retiredReward = 25
        """);

    assertThat(host.reloadConfigFromDisk(false)).isTrue();

    String canonical = Files.readString(configPath);
    assertThat(skill.getConfig().reward).isEqualTo(120D);
    assertThat(canonical).contains("reward = 120.0");
    assertThat(canonical).doesNotContain("retiredReward");

    assertThat(host.reloadConfigFromDisk(false)).isTrue();
    assertThat(Files.readString(configPath)).isEqualTo(canonical);
  }

  @Test
  void snapshotReloadAppliesCapturedContentWithoutRereadingOrRewritingDisk() throws IOException {
    NormalizedSkill skill = TestContent.skill(NormalizedSkill::new);
    ComponentHost host = ComponentHost.of(skill);
    Path configPath = host.getConfigFile().toPath();
    String diskContent = """
        enabled = true
        reward = 7
        """;
    Files.writeString(configPath, diskContent);

    boolean reloaded = host.reloadConfigSnapshot("""
        enabled = true
        reward = 45
        """, configPath.toFile(), false);

    assertThat(reloaded).isTrue();
    assertThat(skill.getConfig().reward).isEqualTo(120D);
    assertThat(Files.readString(configPath)).isEqualTo(diskContent);
  }

  public static final class NormalizedSkill extends SimpleSkill<NormalizedConfig> {
    public NormalizedSkill() {
      super(Id.adapt("normalized_skill"));
      registerConfiguration(NormalizedConfig.class);
    }

    @Override
    protected void normalizeLoadedConfig(NormalizedConfig loadedConfig) {
      if (Double.compare(loadedConfig.reward, 45D) == 0) {
        loadedConfig.reward = 120D;
      }
    }
  }

  public static final class NormalizedConfig extends SkillConfig {
    public double reward = 120D;

    public NormalizedConfig() {
    }
  }
}
