package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAxesIrisRegistrationTest {
  @Test
  void irisFellerRegistrationIsConditionalOnIrisAvailability() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/content/skill/SkillAxes.java"));

    assertThat(source).contains(
        "this(IrisTreeFellerLink.isAvailable());",
        "if (irisTreeFellerAvailable) {",
        "registerAdaptation(new AxeIrisFeller());"
    );

    String adaptationSource = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/axe/AxeIrisFeller.java"));
    assertThat(adaptationSource).contains(
        "public boolean isEnabled()",
        "return IrisTreeFellerLink.isAvailable() && super.isEnabled();"
    );
  }

  @Test
  void irisFellerClaimsBeforeWoodVeinminerRegistration() throws IOException {
    String source = Files.readString(Path.of("src/main/java/art/arcane/adapt/content/skill/SkillAxes.java"));
    int irisFeller = source.indexOf("registerAdaptation(new AxeIrisFeller());");
    int woodVeinminer = source.indexOf("registerAdaptation(new AxeWoodVeinminer());");

    assertThat(irisFeller).isGreaterThanOrEqualTo(0);
    assertThat(woodVeinminer).isGreaterThan(irisFeller);
  }

  @Test
  void irisFellerResourcesExposeTheOptionalDependencyPermissionAndEnglishText() throws IOException {
    String pluginManifest = Files.readString(Path.of("src/main/resources/plugin.yml"));
    String english = Files.readString(Path.of("src/main/resources/en_US.toml"));

    assertThat(pluginManifest).contains("  - Iris", "adapt.use.axeirisfeller:");
    assertThat(english).contains(
        "[axe.iris_feller]",
        "name = \"Iris Feller\"",
        "lore4 = \"hunger points per successfully eroded log\"",
        "lore5 = \"cooldown after Iris accepts the run\"",
        "lore6 = \"Sneak continuously; releasing sneak halts the run\"",
        "lore7 = \"Keep the original axe held; switching or losing it halts the run\"",
        "Each successfully eroded log costs hunger; the run halts when sneaking stops, the original axe is no longer held, or hunger cannot fund the next log."
    );
  }
}
