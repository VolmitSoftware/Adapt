package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TragoulLanceConfigPersistenceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void canonicalRewriteReplacesDamageMultiplierWithFlatLevelEndpoints() throws IOException {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(temporaryDirectory).build(), List.of())) {
      TragoulLance lance = TestContent.adaptation(Id.adapt("tragoul"), TragoulLance::new);
      Path configPath = ComponentHost.of(lance).getConfigFile().toPath();
      Files.createDirectories(configPath.getParent());
      Files.writeString(configPath, """
          enabled = true
          maxLevel = 5
          seekerDelay = 12
          seekerDamageMultiplier = 1.0
          selfDamageMultiplier = 0.25
          unarmoredDamageMultiplier = 3.0
          """);

      assertThat(ComponentHost.of(lance).reloadConfigFromDisk(false)).isTrue();

      String canonical = Files.readString(configPath);
      assertThat(lance.getConfig().selfDamageAtFirstLevel).isEqualTo(6D);
      assertThat(lance.getConfig().selfDamageAtMaxLevel).isEqualTo(2D);
      assertThat(canonical).contains("selfDamageAtFirstLevel = 6.0", "selfDamageAtMaxLevel = 2.0");
      assertThat(canonical).doesNotContain("selfDamageMultiplier");
    }
  }
}
