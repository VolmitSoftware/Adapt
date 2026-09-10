package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptLanguageDownloadTest extends AdaptTestBase {

  @AfterEach
  void closeRemoteCatalog() {
    AdaptLanguage.shutdown();
  }

  @Test
  void sourceManifestRestrictsDownloadsToKnownLocales() {
    Set<String> locales = AdaptLanguage.remote().availableLocales();

    assertThat(locales).hasSize(17).contains("de_DE", "ja-JP", "zh_TW").doesNotContain("en_US");
    assertThatThrownBy(() -> AdaptLanguage.remote().sourceUri("unknown_LOCALE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sourceUriPinsTheSelectedLocaleToTheBuildRevision() {
    URI source = AdaptLanguage.remote().sourceUri("de_DE");

    assertThat(source.getHost()).isEqualTo("raw.githubusercontent.com");
    assertThat(source.getPath()).isEqualTo(
        "/VolmitSoftware/Adapt/"
            + AdaptLanguage.remote().revision()
            + "/src/main/resources/de_DE.toml"
    );
  }

  @Test
  void installedLocalePreservesEditsWhenPrepared() throws Exception {
    Path target = AdaptLanguage.languageFolder().toPath().resolve("de_DE.toml");
    Files.createDirectories(target.getParent());
    Files.copy(Path.of("src/main/resources/de_DE.toml"), target);

    LocalizationSnapshot downloaded = AdaptLanguage.editorOptions().loader().load("de_DE");
    assertThat(downloaded.value(RuntimeMessages.NO_DESCRIPTION_PROVIDED))
        .isEqualTo(new TextValue("Keine Beschreibung"));

    String edited = "[runtime]\nno_description_provided = \"Eigener Text\"\n";
    Files.writeString(target, edited, StandardCharsets.UTF_8);
    LocalizationSnapshot customized = AdaptLanguage.editorOptions().loader().load("de_DE");

    assertThat(customized.value(RuntimeMessages.NO_DESCRIPTION_PROVIDED))
        .isEqualTo(new TextValue("Eigener Text"));
    assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(edited);
  }

  @Test
  void malformedInstalledLocaleIsRejectedWithoutOverwriting() throws Exception {
    Path target = AdaptLanguage.languageFolder().toPath().resolve("de_DE.toml");
    Files.createDirectories(target.getParent());
    Files.writeString(target, "modified", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> AdaptLanguage.editorOptions().loader().load("de_DE"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo("modified");
  }
}
