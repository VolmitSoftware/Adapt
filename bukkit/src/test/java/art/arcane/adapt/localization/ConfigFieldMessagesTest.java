package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.util.config.ConfigDocumentation;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigFieldMessagesTest extends AdaptTestBase {
  private static final List<MessageKey> BUILT_IN_CONFIG_TEXT = BuiltInText.configText();

  @BeforeEach
  void installBuiltInText() {
    BuiltInText.installWithConfigText();
  }

  @AfterEach
  void uninstallBuiltInText() {
    BuiltInText.uninstall();
  }

  @Test
  void configFieldLabelsAndDescriptionsAcceptIndependentOverrides() throws Exception {
    TextKey label = label("core.language");
    LinesKey description = description("core.language");
    String translation = "[adapt.config.fields.core.language]\nlabel = \"Sprache\"\ndescription = [\"Sprache der Menüs.\", \"Übersetzungen werden bei Bedarf geladen.\"]\n";
    LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
        AdaptMessages.catalog(), List.of(AdaptLanguage.parseOverlay("test", "de_DE", translation)),
        PluralSelector.oneOther()));

    assertThat(snapshot.resolve(label, MessageArgs.empty()).template()).isEqualTo("Sprache");
    assertThat(snapshot.resolve(description, MessageArgs.empty()).lines()).containsExactly("Sprache der Menüs.", "Übersetzungen werden bei Bedarf geladen.");
    assertThat(snapshot.resolve(label("core.sql.enabled")).template()).isEqualTo("Enabled");
    Field language = AdaptConfig.class.getDeclaredField("language");
    assertThat(ConfigDocumentation.buildFieldComments("core-config", "core.language", language, "de_DE"))
        .containsExactlyElementsOf(description.english());
    assertThat(AdaptLanguageReference.render(AdaptMessages.catalog())).contains("[adapt.config.fields.core.language]");
  }

  @Test
  void malformedDescriptionsUseEnglishWithoutDiscardingValidLabels() {
    TextKey label = label("core.language");
    LinesKey description = description("core.language");
    String translation = "[adapt.config.fields.core.language]\nlabel = \"Sprache\"\ndescription = [\"Incomplete\"]\n";
    LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
        AdaptMessages.catalog(), List.of(AdaptLanguage.parseOverlay("test", "de_DE", translation)),
        PluralSelector.oneOther()));

    assertThat(snapshot.resolve(label).template()).isEqualTo("Sprache");
    assertThat(snapshot.resolve(description).lines()).containsExactlyElementsOf(description.english());
  }

  @Test
  void nestedAndInheritedFieldsRetainCanonicalMachinePaths() {
    assertThat(label("core.sql.enabled").id()).isEqualTo("adapt.config.fields.core.sql.enabled.label");
    assertThat(label("adaptations.agility.wall_jump.enabled").english()).isEqualTo("Enabled");
    assertThat(ConfigFieldMessages.label("adaptations.adapt:agility/wall_jump.enabled")).isEqualTo("Enabled");
    assertThat(ConfigFieldMessages.declaresLabel("adaptations.adapt:agility/wall_jump.enabled")).isTrue();
    assertThat(description("skills.agility.enabled").english()).isNotEmpty();
  }

  @Test
  void builtInFieldTextIsReadFromTheConfigDocAnnotations() throws Exception {
    ConfigDoc doc = SkillConfig.class.getDeclaredField("skillColor").getAnnotation(ConfigDoc.class);
    MessageKey description = BUILT_IN_CONFIG_TEXT.stream()
        .filter((MessageKey key) -> key.id().equals("adapt.config.fields.skills.agility.skillColor.description"))
        .findFirst().orElseThrow();

    assertThat(((LinesKey) description).english()).containsExactly(doc.value(), "Effect: " + doc.impact());
    assertThat(ConfigFieldMessages.description("skills.adapt:agility.skillColor",
        SkillConfig.class.getDeclaredField("skillColor"))).containsExactly(doc.value(), "Effect: " + doc.impact());
  }

  @Test
  void aConfigClassNoBundleDeclaresIsDocumentedFromItsAnnotations() throws Exception {
    Field chance = AddonConfig.class.getDeclaredField("transmuteChance");
    assertThat(ConfigFieldMessages.declaresLabel("adaptations.alchemist-transmute.transmuteChance")).isFalse();
    assertThat(ConfigFieldMessages.label("adaptations.alchemist-transmute.transmuteChance")).isEqualTo("Transmute Chance");
    assertThat(ConfigFieldMessages.description("adaptations.alchemist-transmute.transmuteChance", chance))
        .containsExactly("Chance per level to transmute a drop.");
  }

  @Test
  void anAddonDeclaresFieldTextUnderItsConventionKeys() {
    TextKey chanceLabel = TextKey.of("alchemist.config.fields.adaptations.alchemy.transmute.transmuteChance.label", "Chance");
    AdaptLanguage.texts().install(TextBundle.declared("alchemist", List.of(chanceLabel), List.of()));
    try {
      String path = "adaptations.alchemist:alchemy/transmute.transmuteChance";
      assertThat(ConfigFieldMessages.declaresLabel(path)).isTrue();
      assertThat(ConfigFieldMessages.label(path)).isEqualTo("Chance");
      assertThat(ConfigFieldMessages.label("skills.alchemist:alchemy.brewXp")).isEqualTo("Brew Xp");
    } finally {
      AdaptLanguage.texts().uninstall("alchemist");
    }
  }

  private static TextKey label(String path) {
    return (TextKey) AdaptLanguage.builtInCatalog().key("adapt.config.fields." + path + ".label");
  }

  private static LinesKey description(String path) {
    return (LinesKey) AdaptLanguage.builtInCatalog().key("adapt.config.fields." + path + ".description");
  }

  static final class AddonConfig {
    @ConfigDoc("Chance per level to transmute a drop.")
    public double transmuteChance = 0.04D;
  }
}
