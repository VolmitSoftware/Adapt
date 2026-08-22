package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationValidationResult;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AdaptLanguageReferenceTest extends AdaptTestBase {

  @Test
  void initializeWritesTheEnglishReferenceFile() {
    AdaptLanguage.initialize();

    Path reference = referencePath();

    assertThat(reference).exists().isRegularFile();
  }

  @Test
  void referenceFileStartsWithAGeneratedHeader() throws Exception {
    AdaptLanguage.initialize();

    String raw = Files.readString(referencePath(), StandardCharsets.UTF_8);

    assertThat(raw).startsWith("#");
    assertThat(raw).contains("en_US");
    assertThat(raw).contains("regenerated");
    assertThat(raw).contains("languages/overrides/en_US.toml");
  }

  @Test
  void referenceFileParsesAsTomlAndKeepsKnownEnglishText() throws Exception {
    AdaptLanguage.initialize();

    LocaleOverlay overlay = parseReference();
    MessageValue missingPermission = overlay.value(CommandRuntimeMessages.MISSING_PERMISSION.id());
    MessageValue playerOnly = overlay.value(CommandRuntimeMessages.PLAYER_ONLY.id());

    assertThat(missingPermission).isInstanceOf(TextValue.class);
    assertThat(((TextValue) missingPermission).template())
        .isEqualTo(CommandRuntimeMessages.MISSING_PERMISSION.english());
    assertThat(playerOnly).isInstanceOf(TextValue.class);
    assertThat(((TextValue) playerOnly).template())
        .isEqualTo(CommandRuntimeMessages.PLAYER_ONLY.english());
  }

  @Test
  void referenceFileCoversTheWholeCatalogWithoutValidationErrors() throws Exception {
    AdaptLanguage.initialize();

    MessageCatalog catalog = AdaptMessages.catalog();
    LocaleOverlay overlay = parseReference();
    LocalizationValidationResult validation = LocalizationValidator.validate(catalog, List.of(overlay));

    assertThat(overlay.values().keySet()).containsExactlyInAnyOrderElementsOf(catalog.byId().keySet());
    assertThat(validation.errors()).isEmpty();
  }

  @Test
  void repeatedInitializeRewritesIdenticalContent() throws Exception {
    AdaptLanguage.initialize();
    String first = Files.readString(referencePath(), StandardCharsets.UTF_8);
    AdaptLanguage.initialize();
    String second = Files.readString(referencePath(), StandardCharsets.UTF_8);

    assertThat(second).isEqualTo(first);
  }

  @Test
  void referenceWriteFailureDoesNotEscapeInitialize() throws Exception {
    Path blocked = referencePath();
    Files.createDirectories(blocked);
    Files.writeString(blocked.resolve("blocker.txt"), "blocked", StandardCharsets.UTF_8);

    assertThatCode(AdaptLanguage::initialize).doesNotThrowAnyException();
    assertThat(blocked).isDirectory();
  }

  @Test
  void referenceWriteLeavesNoTemporaryFilesBehind() throws Exception {
    AdaptLanguage.initialize();
    AdaptLanguage.initialize();

    try (Stream<Path> entries = Files.list(referencePath().getParent())) {
      assertThat(entries.map(path -> path.getFileName().toString()).toList())
          .containsExactlyInAnyOrder("en_US.toml", "overrides");
    }
  }

  private LocaleOverlay parseReference() throws Exception {
    Path reference = referencePath();
    return AdaptLanguage.parseOverlay(
        reference.toString(),
        "en_US",
        Files.readString(reference, StandardCharsets.UTF_8)
    );
  }

  private Path referencePath() {
    return new File(new File(dataFolder, "languages"), "en_US.toml").toPath();
  }
}
