package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptLanguageEditorTest extends AdaptTestBase {
  @BeforeEach
  void initializeServices() throws Exception {
    Field services = Adapt.class.getDeclaredField("services");
    services.setAccessible(true);
    services.set(plugin, new KMap<Class<? extends AdaptService>, AdaptService>());
  }

  @Test
  void savesEnglishDirectlyAndRetainsNeighboringMessages() throws Exception {
    Path file = write("en_US", "# Local formatting\n[runtime]\nno_description_provided = \"Custom English\"\n"
        + "[command.runtime]\nplayer_only = \"Players only\"\n");

    LocalizationSnapshot saved = AdaptLanguage.editorOptions().writer().write(new PluginLanguageEditor.Edit(
        "en_US", RuntimeMessages.NO_DESCRIPTION_PROVIDED.id(), new TextValue("Custom English"), new TextValue("Edited English")));

    assertThat(saved.value(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo(new TextValue("Edited English"));
    assertThat(Files.readString(file)).contains("# Local formatting", "[runtime]", "Edited English", "Players only");
  }

  @Test
  void savesPartialLocaleWhileInvalidSiblingUsesEnglish() throws Exception {
    Path file = write("de_DE", "[runtime]\nno_description_provided = \"Eigener Text\"\n"
        + "[command.runtime]\nmissing_permission = 42\n");

    LocalizationSnapshot saved = AdaptLanguage.editorOptions().writer().write(new PluginLanguageEditor.Edit(
        "de_DE", RuntimeMessages.NO_DESCRIPTION_PROVIDED.id(), new TextValue("Eigener Text"), new TextValue("Neuer Text")));

    assertThat(saved.value(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo(new TextValue("Neuer Text"));
    assertThat(saved.value(CommandRuntimeMessages.MISSING_PERMISSION)).isEqualTo(CommandRuntimeMessages.MISSING_PERMISSION.englishValue());
    assertThat(Files.readString(file)).contains("missing_permission = 42");
  }

  @Test
  void staleEditorValueCannotOverwriteNewerLocalEdit() throws Exception {
    String raw = "[runtime]\nno_description_provided = \"Changed on disk\"\n";
    Path file = write("de_DE", raw);

    assertThatThrownBy(() -> AdaptLanguage.editorOptions().writer().write(new PluginLanguageEditor.Edit(
        "de_DE", RuntimeMessages.NO_DESCRIPTION_PROVIDED.id(), new TextValue("Old value"), new TextValue("Replacement"))))
        .hasMessageContaining("changed");

    assertThat(Files.readString(file)).isEqualTo(raw);
  }

  @Test
  void invalidEditedPlaceholdersCannotReplaceTheFile() throws Exception {
    String raw = "[runtime]\nno_description_provided = \"Eigener Text\"\n";
    Path file = write("de_DE", raw);

    assertThatThrownBy(() -> AdaptLanguage.editorOptions().writer().write(new PluginLanguageEditor.Edit(
        "de_DE", CommandRuntimeMessages.MISSING_PERMISSION.id(), CommandRuntimeMessages.MISSING_PERMISSION.englishValue(),
        new TextValue("Missing permission variable")))).isInstanceOf(IllegalArgumentException.class);

    assertThat(Files.readString(file)).isEqualTo(raw);
  }

  private Path write(String locale, String raw) throws Exception {
    Path file = AdaptLanguage.languageFolder().toPath().resolve(locale + ".toml");
    Files.createDirectories(file.getParent());
    Files.writeString(file, raw);
    return file;
  }
}
