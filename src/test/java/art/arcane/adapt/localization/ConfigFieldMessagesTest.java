package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.util.config.ConfigDocumentation;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.PluralSelector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigFieldMessagesTest extends AdaptTestBase {
  @Test
  void configFieldLabelsAndDescriptionsAcceptIndependentOverrides() throws Exception {
    ConfigFieldMessages.FieldMessages field = ConfigFieldMessages.field("core.language");
    String translation = "[config.fields.core.language]\nlabel = \"Sprache\"\ndescription = [\"Sprache der Menüs.\", \"Übersetzungen werden bei Bedarf geladen.\"]\n";
    LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
        AdaptMessages.catalog(), List.of(AdaptLanguage.parseOverlay("test", "de_DE", translation)),
        PluralSelector.oneOther()));

    assertThat(snapshot.resolve(field.label(), MessageArgs.empty()).template()).isEqualTo("Sprache");
    assertThat(snapshot.resolve(field.description(), MessageArgs.empty()).lines()).containsExactly("Sprache der Menüs.", "Übersetzungen werden bei Bedarf geladen.");
    assertThat(snapshot.resolve(ConfigFieldMessages.field("core.sql.enabled").label()).template()).isEqualTo("Enabled");
    Field language = AdaptConfig.class.getDeclaredField("language");
    assertThat(ConfigDocumentation.buildFieldComments("core-config", "core.language", language, "de_DE"))
        .containsExactlyElementsOf(field.description().english());
    assertThat(AdaptLanguageReference.render(AdaptMessages.catalog())).contains("[config.fields.core.language]");
  }

  @Test
  void malformedDescriptionsUseEnglishWithoutDiscardingValidLabels() {
    ConfigFieldMessages.FieldMessages field = ConfigFieldMessages.field("core.language");
    String translation = "[config.fields.core.language]\nlabel = \"Sprache\"\ndescription = [\"Incomplete\"]\n";
    LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
        AdaptMessages.catalog(), List.of(AdaptLanguage.parseOverlay("test", "de_DE", translation)),
        PluralSelector.oneOther()));

    assertThat(snapshot.resolve(field.label()).template()).isEqualTo("Sprache");
    assertThat(snapshot.resolve(field.description()).lines()).containsExactlyElementsOf(field.description().english());
  }

  @Test
  void nestedAndInheritedFieldsRetainCanonicalMachinePaths() {
    assertThat(ConfigFieldMessages.field("core.sql.enabled").label().id())
        .isEqualTo("config.fields.core.sql.enabled.label");
    assertThat(ConfigFieldMessages.field("adaptations.agility-wall-jump.enabled").label().english())
        .isEqualTo("Enabled");
    assertThat(ConfigFieldMessages.field("skills.agility.enabled").description().english()).isNotEmpty();
  }

  @Test
  void everySkillAndAdaptationConfigIsRepresented() throws Exception {
    Pattern config = Pattern.compile("\\bclass Config\\b");
    Pattern name = Pattern.compile("super\\(\"([a-z0-9-]+)\"");
    for (String kind : List.of("skill", "adaptation")) {
      Path root = Path.of("src/main/java/art/arcane/adapt/content", kind);
      try (Stream<Path> paths = Files.walk(root)) {
        List<Path> sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        for (Path path : sources) {
          String source = Files.readString(path);
          if (!config.matcher(source).find()) {
            continue;
          }
          Matcher matcher = name.matcher(source);
          assertThat(matcher.find()).describedAs("configuration name in %s", path).isTrue();
          assertThat(ConfigFieldMessages.field(kind + "s." + matcher.group(1) + ".enabled"))
              .describedAs("catalog registration for %s", path).isNotNull();
        }
      }
    }
  }
}
