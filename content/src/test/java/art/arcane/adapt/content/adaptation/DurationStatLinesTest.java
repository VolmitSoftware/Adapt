package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.content.adaptation.agility.AgilityArmorUp;
import art.arcane.adapt.content.adaptation.chronos.ChronosTemporalEcho;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DurationStatLinesTest {
  @TempDir
  Path dataFolder;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void durationsComputedAsDoublesKeepTheirShortForm() {
    assertThat(lines(TestContent.adaptation(SkillAgility.ID, AgilityArmorUp::new), 3)).containsExactly(
        "\u00a7a+ 5.4\u00a77 Max Armor",
        "\u00a7e* 5.4s\u00a77 Armor-Up Time",
        "\u00a7e* 8s\u00a77 Armor Decay Time");
  }

  @Test
  void durationsBelowASecondAndWholeMillisecondCooldownsKeepTheirForms() {
    assertThat(lines(TestContent.adaptation(SkillChronos.ID, ChronosTemporalEcho::new), 1)).containsExactly(
        "\u00a7a+ 800ms\u00a77 Echo Delay",
        "\u00a7a+ 54%\u00a77 Echo Velocity Factor",
        "\u00a7e* 4.5 seconds\u00a77 Echo Cooldown");
  }

  private static List<String> lines(art.arcane.adapt.api.content.Adaptation<?> adaptation, int level) {
    StatLines stats = new StatLines();
    adaptation.addStats(level, stats);
    return stats.lines();
  }
}
