package art.arcane.adapt.content.skill;

import art.arcane.adapt.localization.BuiltInText;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAxesIrisRegistrationTest {
  private static final MessageCatalog CATALOG = BuiltInText.catalog();
  @Test
  void irisFellerResourcesExposeTheOptionalDependencyPermissionAndEnglishText() throws IOException {
    String pluginManifest = Files.readString(Path.of("src/main/resources/plugin.yml"));
    TextValue name = (TextValue) CATALOG.require("adapt.axe.iris_feller.name").englishValue();
    TextValue lore4 = (TextValue) CATALOG.require("adapt.axe.iris_feller.lore4").englishValue();
    TextValue lore5 = (TextValue) CATALOG.require("adapt.axe.iris_feller.lore5").englishValue();
    TextValue lore6 = (TextValue) CATALOG.require("adapt.axe.iris_feller.lore6").englishValue();
    TextValue lore7 = (TextValue) CATALOG.require("adapt.axe.iris_feller.lore7").englishValue();
    TextValue description = (TextValue) CATALOG.require("adapt.axe.iris_feller.description").englishValue();

    assertThat(pluginManifest).contains("  - Iris");
    assertThat(art.arcane.adapt.engine.AdaptPermissionRegistrar.useNode(Id.adapt("axe/iris_feller")))
        .isEqualTo("adapt.use.adapt.axe.iris_feller");
    assertThat(name.template()).isEqualTo("Iris Feller");
    assertThat(lore4.template()).isEqualTo("hunger points per successfully eroded log");
    assertThat(lore5.template()).isEqualTo("cooldown after Iris accepts the run");
    assertThat(lore6.template()).isEqualTo("Sneak continuously; releasing sneak halts the run");
    assertThat(lore7.template()).isEqualTo("Keep the original axe held; switching or losing it halts the run");
    assertThat(description.template()).contains(
        "Each successfully eroded log costs hunger",
        "the run halts when sneaking stops",
        "the original axe is no longer held",
        "hunger cannot fund the next log"
    );
  }
}
