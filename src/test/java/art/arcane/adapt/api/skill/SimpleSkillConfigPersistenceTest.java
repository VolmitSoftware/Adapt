package art.arcane.adapt.api.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleSkillConfigPersistenceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void optedInNormalizationRewritesExistingSkillConfig() throws IOException {
    Path configPath = temporaryDirectory.resolve("normalized-skill.toml");
    Files.writeString(configPath, """
        enabled = true
        reward = 45
        retiredReward = 25
        """);
    TestSkill skill = new TestSkill(configPath);

    assertThat(skill.reloadConfigFromDisk(false)).isTrue();

    String canonical = Files.readString(configPath);
    assertThat(skill.getConfig().reward).isEqualTo(120D);
    assertThat(canonical).contains("reward = 120.0");
    assertThat(canonical).doesNotContain("retiredReward");

    assertThat(skill.reloadConfigFromDisk(false)).isTrue();
    assertThat(Files.readString(configPath)).isEqualTo(canonical);
  }

  private static final class TestSkill extends SimpleSkill<TestConfig> {
    private final File configFile;
    private final File legacyConfigFile;

    private TestSkill(Path configPath) {
      super("normalized-skill", "");
      configFile = configPath.toFile();
      legacyConfigFile = configPath.resolveSibling("normalized-skill.json").toFile();
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
      if (Double.compare(loadedConfig.reward, 45D) == 0) {
        loadedConfig.reward = 120D;
      }
    }

    @Override
    protected boolean shouldCanonicalizeConfigOnLoad() {
      return true;
    }

    @Override
    public boolean isEnabled() {
      return getConfig().enabled;
    }
  }

  public static final class TestConfig {
    boolean enabled = true;
    double reward = 120D;

    public TestConfig() {
    }
  }
}
