package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AdaptLanguageDownloadCacheTest extends AdaptTestBase {

  @AfterEach
  void restoreEnglishSnapshot() {
    AdaptLanguage.initialize();
  }

  @Test
  void downloadedLocaleProvidesTheSelectedLanguage() throws Exception {
    writeDownloadedLocale("de_DE");
    AdaptConfig config = localeConfig("de_DE");

    String rendered;
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      assertThat(AdaptLanguage.reload()).isTrue();
      rendered = AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED);
    }

    assertThat(rendered).isEqualTo("Keine Beschreibung");
    assertThat(referencePath()).exists().isRegularFile();
  }

  @Test
  void missingDownloadFallsBackToCodeOwnedEnglish() {
    AdaptConfig config = localeConfig("de_DE");

    String rendered;
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      assertThat(AdaptLanguage.reload()).isTrue();
      rendered = AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED);
    }

    assertThat(rendered).isEqualTo(RuntimeMessages.NO_DESCRIPTION_PROVIDED.english());
  }

  @Test
  void sparseOverrideTakesPriorityOverDownloadedLocale() throws Exception {
    writeDownloadedLocale("de_DE");
    Path override = writeOverride("de_DE", "Eigener Text");
    AdaptConfig config = localeConfig("de_DE");

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      assertThat(AdaptLanguage.reload()).isTrue();
      assertThat(AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo("Eigener Text");
    }

    assertThat(override).exists();
  }

  @Test
  void corruptDownloadDoesNotBlockAValidOverride() throws Exception {
    Path downloaded = AdaptLanguageDownload.localeFile("de_DE").toPath();
    Files.createDirectories(downloaded.getParent());
    Files.writeString(downloaded, "[unknown]\nkey = \"invalid\"\n", StandardCharsets.UTF_8);
    writeOverride("de_DE", "Nur eigener Text");
    AdaptConfig config = localeConfig("de_DE");

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      assertThat(AdaptLanguage.reload()).isTrue();
      assertThat(AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo("Nur eigener Text");
    }
  }

  @Test
  void englishReloadCreatesOnlyReferenceAndLanguageDirectories() throws Exception {
    AdaptConfig config = localeConfig("en_US");

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      assertThat(AdaptLanguage.reload()).isTrue();
    }

    try (Stream<Path> entries = Files.list(referencePath().getParent())) {
      assertThat(entries.map(path -> path.getFileName().toString()).toList())
          .containsExactlyInAnyOrder("en_US.toml", "overrides", "downloaded");
    }
  }

  @Test
  void overrideSnapshotUsesCapturedContentOverDownloadedLocaleWithoutWritingDisk() throws Exception {
    writeDownloadedLocale("de_DE");
    AdaptConfig config = localeConfig("de_DE");
    Path override = writeOverride("de_DE", "Disk value");

    String rendered;
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      assertThat(AdaptLanguage.reloadOverrideSnapshot(
          override.toFile(),
          "[runtime]\nno_description_provided = \"Captured value\"\n"
      )).isTrue();
      rendered = AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED);
    }

    assertThat(rendered).isEqualTo("Captured value");
    assertThat(Files.readString(override, StandardCharsets.UTF_8))
        .isEqualTo("[runtime]\nno_description_provided = \"Disk value\"\n");
    assertThat(referencePath()).doesNotExist();
  }

  private AdaptConfig localeConfig(String locale) {
    AdaptConfig config = mock(AdaptConfig.class);
    lenient().when(config.getLanguage()).thenReturn(locale);
    lenient().when(config.isAutomaticGradients()).thenReturn(false);
    return config;
  }

  private Path writeDownloadedLocale(String locale) throws Exception {
    Path target = AdaptLanguageDownload.localeFile(locale).toPath();
    Files.createDirectories(target.getParent());
    Files.copy(Path.of("src/main/resources", locale + ".toml"), target, StandardCopyOption.REPLACE_EXISTING);
    return target;
  }

  private Path writeOverride(String locale, String value) throws Exception {
    Path override = new File(dataFolder, "languages/overrides/" + locale + ".toml").toPath();
    Files.createDirectories(override.getParent());
    Files.writeString(
        override,
        "[runtime]\nno_description_provided = \"" + value + "\"\n",
        StandardCharsets.UTF_8
    );
    return override;
  }

  private Path referencePath() {
    return new File(dataFolder, "languages/en_US.toml").toPath();
  }
}
