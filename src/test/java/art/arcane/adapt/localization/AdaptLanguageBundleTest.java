package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AdaptLanguageBundleTest extends AdaptTestBase {
  private static final String BUNDLED = "# Adapt language overlay: de_DE\n\n"
      + "[runtime]\nno_description_provided = \"Keine Beschreibung angegeben\"\n";

  @AfterEach
  void restoreEnglishSnapshot() {
    AdaptLanguage.initialize();
  }

  @Test
  void extractionWritesTheBundledLocaleWithAGeneratedHeader() throws Exception {
    stubBundledResource("de_DE");

    boolean written = AdaptLanguageBundle.extract("de_DE");
    String raw = Files.readString(bundlePath("de_DE"), StandardCharsets.UTF_8);

    assertThat(written).isTrue();
    assertThat(raw).startsWith("# Adapt bundled locale: de_DE");
    assertThat(raw).contains("regenerated on every boot and reload");
    assertThat(raw).contains("languages/overrides/de_DE.toml");
    assertThat(raw).contains(BUNDLED);
  }

  @Test
  void reExtractionOverwritesManualEdits() throws Exception {
    stubBundledResource("de_DE");
    AdaptLanguageBundle.extract("de_DE");
    Files.writeString(bundlePath("de_DE"), "# hand edited\n", StandardCharsets.UTF_8);

    boolean written = AdaptLanguageBundle.extract("de_DE");
    String raw = Files.readString(bundlePath("de_DE"), StandardCharsets.UTF_8);

    assertThat(written).isTrue();
    assertThat(raw).doesNotContain("hand edited");
    assertThat(raw).contains(BUNDLED);
  }

  @Test
  void englishLocaleIsNeverExtracted() {
    stubBundledResource("en_US");

    boolean written = AdaptLanguageBundle.extract("en_US");

    assertThat(written).isFalse();
    assertThat(bundlePath("en_US")).doesNotExist();
  }

  @Test
  void missingBundledResourceWritesNothing() {
    assertThatCode(() -> assertThat(AdaptLanguageBundle.extract("de_DE")).isFalse())
        .doesNotThrowAnyException();

    assertThat(bundlePath("de_DE")).doesNotExist();
  }

  @Test
  void invalidLocaleNamesAreRejectedBeforeAnyWrite() {
    assertThat(AdaptLanguageBundle.extract("../evil")).isFalse();
    assertThat(AdaptLanguageBundle.extract("de_DE/../../evil")).isFalse();
    assertThat(AdaptLanguageBundle.extract("")).isFalse();
    assertThat(AdaptLanguageBundle.extract(null)).isFalse();

    assertThat(new File(dataFolder, "languages").toPath()).doesNotExist();
  }

  @Test
  void reloadExtractsTheActiveNonEnglishLocaleAlongsideTheReference() throws Exception {
    stubBundledResource("de_DE");
    AdaptConfig config = mock(AdaptConfig.class);
    lenient().when(config.getLanguage()).thenReturn("de_DE");
    lenient().when(config.isAutomaticGradients()).thenReturn(false);

    boolean reloaded;
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      reloaded = AdaptLanguage.reload();
    }

    assertThat(reloaded).isTrue();
    assertThat(bundlePath("de_DE")).exists().isRegularFile();
    assertThat(bundlePath("en_US")).exists().isRegularFile();
    assertThat(Files.readString(bundlePath("de_DE"), StandardCharsets.UTF_8))
        .contains("languages/overrides/de_DE.toml");
  }

  @Test
  void reloadOnEnglishProducesOnlyTheReferenceFile() throws Exception {
    AdaptConfig config = mock(AdaptConfig.class);
    lenient().when(config.getLanguage()).thenReturn("en_US");
    lenient().when(config.isAutomaticGradients()).thenReturn(false);

    boolean reloaded;
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      reloaded = AdaptLanguage.reload();
    }

    assertThat(reloaded).isTrue();
    try (Stream<Path> entries = Files.list(bundlePath("en_US").getParent())) {
      assertThat(entries.map(path -> path.getFileName().toString()).toList())
          .containsExactlyInAnyOrder("en_US.toml", "overrides");
    }
  }

  @Test
  void overrideSnapshotReloadUsesCapturedContentWithoutRereadingOrWritingDisk() throws Exception {
    AdaptConfig config = mock(AdaptConfig.class);
    lenient().when(config.getLanguage()).thenReturn("de_DE");
    lenient().when(config.isAutomaticGradients()).thenReturn(false);
    Path override = new File(dataFolder, "languages/overrides/de_DE.toml").toPath();
    Files.createDirectories(override.getParent());
    String diskContent = "[runtime]\nno_description_provided = \"Disk value\"\n";
    Files.writeString(override, diskContent, StandardCharsets.UTF_8);

    boolean reloaded;
    String rendered;
    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      reloaded = AdaptLanguage.reloadOverrideSnapshot(
          override.toFile(),
          "[runtime]\nno_description_provided = \"Captured value\"\n"
      );
      rendered = AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED);
    }

    assertThat(reloaded).isTrue();
    assertThat(rendered).isEqualTo("Captured value");
    assertThat(Files.readString(override, StandardCharsets.UTF_8)).isEqualTo(diskContent);
    assertThat(bundlePath("en_US")).doesNotExist();
  }

  private void stubBundledResource(String locale) {
    lenient().when(plugin.getResource(locale + ".toml"))
        .thenAnswer(invocation -> new ByteArrayInputStream(BUNDLED.getBytes(StandardCharsets.UTF_8)));
  }

  private Path bundlePath(String locale) {
    return new File(new File(dataFolder, "languages"), locale + ".toml").toPath();
  }
}
