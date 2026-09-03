package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdaptLanguageEditorTest extends AdaptTestBase {
  private PluginLanguageService languages;
  private PluginLanguageEditor editor;

  @BeforeEach
  void prepareEditor() throws Exception {
    Path cache = AdaptLanguage.remote().cacheFile("fr_FR");
    Files.createDirectories(cache.getParent());
    Files.copy(Path.of("src/main/resources/fr_FR.toml"), cache);
    PluginLanguageEditor.Options options = AdaptLanguage.editorOptions();
    LocalizationSnapshot english = LocalizationSnapshot.create(
        LocalizationCandidate.english(AdaptMessages.catalog(), PluralSelector.oneOther()));
    languages = new PluginLanguageService(new PluginLanguageService.Options(
        dataFolder.toPath().resolve("players.properties"), VolmitLocales::all, () -> "en_US", () -> english,
        options.loader()::load, (locale, snapshot) -> {
          throw new AssertionError("Editing must not select a server language");
        }, Logger.getLogger("AdaptLanguageEditorTest")));
    editor = new PluginLanguageEditor(languages, options);
  }

  @AfterEach
  void closeEditor() {
    editor.close();
    languages.close();
    AdaptLanguage.shutdown();
  }

  @Test
  void savesOneLocaleAndRefreshesItsPersonalSnapshotWithoutSelectingIt() throws Exception {
    UUID player = UUID.randomUUID();
    languages.selectPlayer(player, "fr_FR").get(5, TimeUnit.SECONDS);
    String active = AdaptLanguage.activeLocale();
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    TextValue value = new TextValue("Autorisation {permission}");
    editor.save(new PluginLanguageEditor.Edit("fr_FR", CommandRuntimeMessages.MISSING_PERMISSION.id(),
        original.snapshot().value(CommandRuntimeMessages.MISSING_PERMISSION), value)).get(5, TimeUnit.SECONDS);

    Path file = AdaptLanguage.overrideFolder().toPath().resolve("fr_FR.toml");
    assertThat(Files.readString(file)).contains("Autorisation {permission}");
    assertThat(AdaptLanguage.editorOptions().loader().load("fr_FR").value(CommandRuntimeMessages.MISSING_PERMISSION)).isEqualTo(value);
    assertThat(languages.snapshot(player).value(CommandRuntimeMessages.MISSING_PERMISSION)).isEqualTo(value);
    assertThat(languages.playerLocale(player)).contains("fr_FR");
    assertThat(languages.defaultLocale()).isEqualTo("en_US");
    assertThat(AdaptLanguage.activeLocale()).isEqualTo(active);
    assertThat(Files.exists(AdaptLanguage.overrideFolder().toPath().resolve("en_US.toml"))).isFalse();
  }

  @Test
  void invalidMessageLeavesTheLocaleFileIntact() throws Exception {
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    editor.save(new PluginLanguageEditor.Edit("fr_FR", CommandRuntimeMessages.MISSING_PERMISSION.id(),
        original.snapshot().value(CommandRuntimeMessages.MISSING_PERMISSION), new TextValue("Permission {permission}")))
        .get(5, TimeUnit.SECONDS);
    Path file = AdaptLanguage.overrideFolder().toPath().resolve("fr_FR.toml");
    byte[] before = Files.readAllBytes(file);
    PluginLanguageEditor.Document saved = editor.load("fr_FR").get(5, TimeUnit.SECONDS);

    assertThrows(ExecutionException.class, () -> editor.save(new PluginLanguageEditor.Edit("fr_FR",
        CommandRuntimeMessages.MISSING_PERMISSION.id(), saved.snapshot().value(CommandRuntimeMessages.MISSING_PERMISSION),
        new TextValue("Missing placeholder"))).get(5, TimeUnit.SECONDS));
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
  }

  @Test
  void staleMessagePreservesAnExternalFileEdit() throws Exception {
    PluginLanguageEditor.Document original = editor.load("fr_FR").get(5, TimeUnit.SECONDS);
    Path file = AdaptLanguage.overrideFolder().toPath().resolve("fr_FR.toml");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "[command.runtime]\nmissing_permission = \"External {permission}\"\n");
    byte[] before = Files.readAllBytes(file);

    assertThrows(ExecutionException.class, () -> editor.save(new PluginLanguageEditor.Edit("fr_FR",
        CommandRuntimeMessages.MISSING_PERMISSION.id(), original.snapshot().value(CommandRuntimeMessages.MISSING_PERMISSION),
        new TextValue("Autorisation {permission}"))).get(5, TimeUnit.SECONDS));
    assertThat(Files.readAllBytes(file)).isEqualTo(before);
    assertThat(languages.defaultLocale()).isEqualTo("en_US");
  }
}
