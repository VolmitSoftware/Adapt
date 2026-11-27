package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.MessageSink;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LinesValue;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextStoreTest extends AdaptTestBase {
  private static final TextKey NAME = TextKey.of("demo.skill.name", "Tinkering");
  private static final TextKey DESCRIPTION = TextKey.of("demo.skill.description", "Build {count} small things.");
  private static final TextKey LABEL = TextKey.of("broken.label", "Broken label");

  private final TextStore texts = AdaptLanguage.texts();

  @BeforeEach
  void englishAndAnEmptyGermanFile() throws Exception {
    BuiltInText.uninstall();
    writeLanguage("de_DE.toml", "");
    writeLanguage("fr_FR.toml", "");
    AdaptLanguage.reloadPassive();
  }

  @AfterEach
  void uninstallTestBundles() {
    texts.uninstall("demo");
    texts.uninstall("broken");
    texts.uninstall("coded");
  }

  @Test
  void resourceBundleResolvesEnglishAndItsTranslation() throws Exception {
    TextBundle demo = demo();
    texts.install(demo);

    assertThat(demo.keys()).contains(NAME, DESCRIPTION, LinesKey.of("demo.gears.lore", "First line", "Second line"));
    assertThat(demo.translations()).containsOnlyKeys("de_DE");
    assertThat(demo.problems()).anyMatch(problem -> problem.contains("demo.skill.unknown"));
    assertThat(AdaptLanguage.text(NAME)).contains("Tinkering");
    assertThat(AdaptLanguage.text(DESCRIPTION, trusted("count", 3))).contains("Build 3 small things.");

    LocalizationSnapshot german = AdaptLanguage.prepareLocale("de_DE");
    assertThat(german.value(NAME)).isEqualTo(new TextValue("Basteln"));
    assertThat(german.value(DESCRIPTION)).isEqualTo(DESCRIPTION.englishValue());
    assertThat(german.sourceLocale(DESCRIPTION)).isEqualTo(VolmitLocales.ENGLISH);
  }

  @Test
  void adminOverridesWinOverTheBundleInEveryLocale() throws Exception {
    writeLanguage("demo/de_DE.toml", "[demo.skill]\nname = \"Werkeln\"\n");
    writeLanguage("demo/en_US.toml", "[demo.skill]\nname = \"Fiddling\"\n");
    texts.install(demo());

    assertThat(AdaptLanguage.text(NAME)).contains("Fiddling");
    assertThat(AdaptLanguage.prepareLocale("de_DE").value(NAME)).isEqualTo(new TextValue("Werkeln"));
    assertThat(AdaptLanguage.prepareLocale("fr_FR").value(NAME)).isEqualTo(NAME.englishValue());
  }

  @Test
  void uninstallRemovesTheKeysAndBumpsTheRevision() {
    texts.install(demo());
    long installed = texts.revision();
    assertThat(texts.uninstall("demo")).isTrue();

    assertThat(texts.revision()).isGreaterThan(installed);
    assertThat(texts.bundle("demo")).isEmpty();
    assertThat(texts.catalog().key(NAME.id())).isNull();
    assertThat(AdaptLanguage.text(NAME)).isEqualTo("[missing:demo.skill.name]");
    assertThat(texts.uninstall("demo")).isFalse();
  }

  @Test
  void undeclaredKeysRenderAsMarkedFallbacksWithoutThrowing() {
    MessageKey standIn = AdaptLanguage.key("nobody.declares.this");

    assertThat(AdaptLanguage.declared("nobody.declares.this")).isNull();
    assertThat(AdaptLanguage.text(standIn)).isEqualTo("[missing:nobody.declares.this]");
    assertThat(AdaptLanguage.text(TextKey.of("nobody.else", "Never shown"))).isEqualTo("[missing:nobody.else]");
    assertThat(AdaptLanguage.template(TextKey.of("nobody.else", "Never shown"))).isEqualTo("[missing:nobody.else]");
    assertThat(AdaptLanguage.text(AdaptLanguage.key("other:addon key"))).isEqualTo("[missing:other_addon_key]");
    assertThat(AdaptLanguage.key(RuntimeMessages.NO_DESCRIPTION_PROVIDED.id())).isEqualTo(RuntimeMessages.NO_DESCRIPTION_PROVIDED);
  }

  @Test
  void aBrokenFileOnlyAffectsItsOwnBundleAndLocale() throws Exception {
    writeLanguage("de_DE.toml", "[adapt.runtime]\nno_description_provided = \"Keine Beschreibung\"\n");
    writeLanguage("demo/de_DE.toml", "[[[ not toml\n");
    TextBundle broken = TextBundle.resources("broken", getClass().getClassLoader(), "text-bundles/broken");
    texts.install(demo());
    texts.install(broken);

    assertThat(broken.translations()).containsOnlyKeys("fr_FR");
    assertThat(broken.problems()).anyMatch(problem -> problem.contains("de_DE.toml"));
    LocalizationSnapshot german = AdaptLanguage.prepareLocale("de_DE");
    assertThat(german.value(NAME)).isEqualTo(new TextValue("Basteln"));
    assertThat(german.value(LABEL)).isEqualTo(LABEL.englishValue());
    assertThat(german.value(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo(new TextValue("Keine Beschreibung"));
    assertThat(AdaptLanguage.prepareLocale("fr_FR").value(LABEL)).isEqualTo(new TextValue("Etiquette"));
  }

  @Test
  void codeDeclaredBundlesTranslateAndConflictsAreRejected() throws Exception {
    LinesKey lines = LinesKey.of("coded.lines", "One", "Two");
    LocaleOverlay german = LocaleOverlay.builder("coded:de_DE", "de_DE")
        .lines("coded.lines", "Eins", "Zwei")
        .text("coded.unknown", "Fehlt")
        .build();
    TextBundle coded = TextBundle.declared("coded", List.of(lines), List.of(german));
    texts.install(coded);

    assertThat(coded.problems()).singleElement().asString().contains("coded.unknown");
    assertThat(AdaptLanguage.prepareLocale("de_DE").value(lines)).isEqualTo(new LinesValue(List.of("Eins", "Zwei")));
    assertThatThrownBy(() -> texts.install(TextBundle.declared("demo", List.of(lines), List.of())))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("coded.lines (coded)");
    assertThatThrownBy(() -> texts.install(TextBundle.declared("other",
        List.of(RuntimeMessages.NO_DESCRIPTION_PROVIDED), List.of())))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("(adapt)");
    assertThatThrownBy(() -> texts.install(TextBundle.declared(TextBundle.BUILT_IN,
        List.of(RuntimeMessages.NO_DESCRIPTION_PROVIDED), List.of())))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("(adapt)");
    assertThatThrownBy(() -> TextBundle.declared(".upstream", List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(texts.bundle("demo")).isEmpty();
  }

  @Test
  void theBuiltInAddonsMessagesJoinTheBuiltInCatalogUntilItIsUninstalled() throws Exception {
    TextKey content = TextKey.of("adapt.tinkering.gears.name", "Gears");
    Set<String> engine = Set.copyOf(AdaptMessages.catalog().ids());
    texts.install(TextBundle.declared(TextBundle.BUILT_IN, List.of(content), List.of()));
    try {
      assertThat(AdaptLanguage.builtInCatalog().ids()).containsAll(engine).contains(content.id());
      assertThat(texts.bundles()).extracting(TextBundle::namespace).containsExactly(TextBundle.BUILT_IN);
      assertThat(AdaptLanguage.declared(content.id())).isEqualTo(content);
      assertThat(AdaptLanguage.declared("tinkering.gears.name")).isNull();
      assertThat(AdaptLanguage.editorOptions().loader().load("de_DE").catalog().ids()).contains(content.id());
    } finally {
      assertThat(texts.uninstall(TextBundle.BUILT_IN)).isTrue();
    }
    assertThat(AdaptLanguage.builtInCatalog().ids()).isEqualTo(engine);
    assertThat(texts.uninstall(TextBundle.BUILT_IN)).isFalse();
  }

  @Test
  void aTomlBundleDeclaresPluralTablesAndTheBuiltInNamespaceIsPrefixedLikeAnyOther() {
    MessageBundle toml = new MessageBundle() {
      @Override
      public List<String> locales() {
        return List.of(MessageBundle.DEFAULT_LOCALE);
      }

      @Override
      public void load(String locale, MessageSink sink) {
        sink.toml("[gears]\none = \"{count} gear\"\nother = \"{count} gears\"\n\n[crates]\none = \"Box\"\nother = \"Boxes\"\n");
      }
    };

    TextBundle addon = TextBundle.messages("demo", List.of(toml));
    TextBundle builtIn = TextBundle.messages(TextBundle.BUILT_IN, List.of(toml));

    assertThat(addon.keys()).extracting(MessageKey::id)
        .containsExactlyInAnyOrder("demo.gears", "demo.crates.one", "demo.crates.other");
    assertThat(addon.keys()).filteredOn((MessageKey key) -> key.id().equals("demo.gears")).singleElement()
        .isInstanceOfSatisfying(PluralKey.class,
            (PluralKey plural) -> assertThat(plural.selectorArgument()).isEqualTo("count"));
    assertThat(builtIn.keys()).extracting(MessageKey::id)
        .containsExactlyInAnyOrder("adapt.gears", "adapt.crates.one", "adapt.crates.other");
  }

  @Test
  void builtInCatalogAndEditorStayBuiltIn() throws Exception {
    Set<String> builtIn = Set.copyOf(AdaptMessages.catalog().ids());
    texts.install(demo());

    assertThat(AdaptMessages.catalog().ids()).isEqualTo(builtIn).doesNotContain(NAME.id());
    assertThat(texts.catalog().ids()).containsAll(builtIn).contains(NAME.id());
    assertThat(texts.bundles()).extracting(TextBundle::namespace).containsExactly(TextBundle.BUILT_IN, "demo");
    assertThat(AdaptLanguage.editorOptions().loader().load("de_DE").catalog().ids()).isEqualTo(builtIn);
  }

  private TextBundle demo() {
    return TextBundle.resources("demo", getClass().getClassLoader(), "/text-bundles/demo/");
  }

  private static void writeLanguage(String relative, String raw) throws Exception {
    Path file = AdaptLanguage.languageFolder().toPath().resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, raw);
  }
}
