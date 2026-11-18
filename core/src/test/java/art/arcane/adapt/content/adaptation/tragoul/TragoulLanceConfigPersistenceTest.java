package art.arcane.adapt.content.adaptation.tragoul;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TragoulLanceConfigPersistenceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void canonicalRewriteReplacesDamageMultiplierWithFlatLevelEndpoints() throws IOException {
    Path configPath = temporaryDirectory.resolve("tragoul-lance.toml");
    Files.writeString(configPath, """
        enabled = true
        maxLevel = 5
        seekerDelay = 12
        seekerDamageMultiplier = 1.0
        selfDamageMultiplier = 0.25
        unarmoredDamageMultiplier = 3.0
        """);
    TestLance lance = new TestLance(configPath);

    assertThat(lance.reloadConfigFromDisk(false)).isTrue();

    String canonical = Files.readString(configPath);
    assertThat(lance.getConfig().selfDamageAtFirstLevel).isEqualTo(6D);
    assertThat(lance.getConfig().selfDamageAtMaxLevel).isEqualTo(2D);
    assertThat(canonical).contains("selfDamageAtFirstLevel = 6.0", "selfDamageAtMaxLevel = 2.0");
    assertThat(canonical).doesNotContain("selfDamageMultiplier");
  }

  private static final class TestLance extends TragoulLance {
    private final File configFile;

    private TestLance(Path configPath) {
      configFile = configPath.toFile();
    }

    @Override
    protected File getConfigFile() {
      return configFile;
    }

  }
}
