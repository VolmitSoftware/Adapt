package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.volmlib.util.localization.LanguageSourceManifest;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptLanguageDownloadTest extends AdaptTestBase {
  private static final long ASYNC_TIMEOUT_SECONDS = 30L;

  private LanguageLoopback repository;

  @AfterEach
  void closeLanguageSource() {
    AdaptLanguage.shutdown();
    if (repository != null) {
      repository.close();
    }
  }

  @Test
  void sourceManifestTracksTheRepositoryBranchAndKnownLocales() {
    LanguageSourceManifest manifest = AdaptLanguage.upstream().manifest();

    assertThat(manifest.repository()).isEqualTo("VolmitSoftware/Adapt");
    assertThat(manifest.branch()).isNotBlank();
    assertThat(manifest.path()).isEqualTo("src/main/resources");
    assertThat(manifest.filePath("de_DE")).isEqualTo("src/main/resources/de_DE.toml");
    assertThat(manifest.locales()).hasSize(17).contains("de_DE", "ja-JP", "zh_TW").doesNotContain("en_US");
    assertThat(AdaptLanguage.isRepositoryLocale("unknown_LOCALE")).isFalse();
    assertThat(AdaptLanguage.availableLocales()).contains("en_US", "de_DE", "ja-JP");
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

  @Test
  void customLocaleResolvesThroughItsMetaBaseChainBeforeEnglish() throws Exception {
    Path folder = AdaptLanguage.languageFolder().toPath();
    Files.createDirectories(folder);
    Files.writeString(folder.resolve("de_DE.toml"), "[runtime]\nno_description_provided = \"Keine Beschreibung\"\n"
        + "[command.runtime]\nplayer_only = \"Nur im Spiel\"\n", StandardCharsets.UTF_8);
    Files.writeString(folder.resolve("pirate.toml"), "[meta]\nname = \"Pirate Speak\"\nbase = \"de_DE\"\n\n"
        + "[runtime]\nno_description_provided = \"Arr, no tale be told\"\n", StandardCharsets.UTF_8);
    AdaptLanguage.initialize();

    LocalizationSnapshot pirate = AdaptLanguage.editorOptions().loader().load("pirate");

    assertThat(pirate.value(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo(new TextValue("Arr, no tale be told"));
    assertThat(pirate.value(CommandRuntimeMessages.PLAYER_ONLY)).isEqualTo(new TextValue("Nur im Spiel"));
    assertThat(pirate.value(CommandRuntimeMessages.MISSING_PERMISSION))
        .isEqualTo(CommandRuntimeMessages.MISSING_PERMISSION.englishValue());
    assertThat(AdaptLanguage.availableLocales()).contains("pirate");
    assertThat(AdaptLanguage.localeDisplayName("pirate")).isEqualTo("Pirate Speak");
    assertThat(AdaptLanguage.displayNames().displayName("pirate")).isEqualTo("Pirate Speak");
    assertThat(AdaptLanguage.localeDisplayName("de_DE")).isNotEqualTo("de_DE");
  }

  @Test
  void englishReferenceMergesCatalogChangesWithoutLosingOperatorEdits() throws Exception {
    AdaptLanguage.initialize();
    Path english = AdaptLanguage.languageFolder().toPath().resolve("en_US.toml");
    Path previous = AdaptLanguage.languageFolder().toPath().resolve(".upstream/en_US.toml");
    String reference = Files.readString(english, StandardCharsets.UTF_8);
    assertThat(previous).hasContent(reference);
    String playerOnly = "player_only = \"" + CommandRuntimeMessages.PLAYER_ONLY.english() + "\"";
    String missingPermission = "missing_permission = \"" + CommandRuntimeMessages.MISSING_PERMISSION.english() + "\"";
    String noDescription = "no_description_provided = \"" + RuntimeMessages.NO_DESCRIPTION_PROVIDED.english() + "\"";
    assertThat(reference).contains(playerOnly, missingPermission, noDescription);
    Files.writeString(previous, reference.replace(playerOnly, "player_only = \"Old catalog text\"")
        .replace(noDescription + "\n", ""), StandardCharsets.UTF_8);
    Files.writeString(english, reference.replace(playerOnly, "player_only = \"Old catalog text\"")
        .replace(missingPermission, "missing_permission = \"Operator wording {permission}\"")
        .replace(noDescription + "\n", ""), StandardCharsets.UTF_8);

    AdaptLanguage.initialize();

    String merged = Files.readString(english, StandardCharsets.UTF_8);
    assertThat(merged).contains(playerOnly, noDescription, "missing_permission = \"Operator wording {permission}\"");
    assertThat(previous).hasContent(AdaptLanguageReference.render(AdaptMessages.catalog()));
  }

  @Test
  void languageUpdateMergesUpstreamChangesAndKeepsManualEditsAndCustomFiles() throws Exception {
    repository = new LanguageLoopback();
    repository.publish(LanguageLoopback.REVISION_ONE, "fi_FI", "[runtime]\nno_description_provided = \"Ei kuvausta\"\n"
        + "[command.runtime]\nplayer_only = \"Vain pelissä\"\n");
    repository.connect();
    AdaptLanguage.initialize();
    Path folder = AdaptLanguage.languageFolder().toPath();
    Path finnish = folder.resolve("fi_FI.toml");
    AdaptLanguage.editorOptions().loader().load("fi_FI");
    assertThat(finnish).exists();
    String installed = Files.readString(finnish, StandardCharsets.UTF_8);
    Files.writeString(finnish, installed.replace("Vain pelissä", "Oma muokkaus"), StandardCharsets.UTF_8);
    String custom = "[meta]\nname = \"Pirate Speak\"\nbase = \"fi_FI\"\n\n[runtime]\nno_description_provided = \"Arr\"\n";
    Files.writeString(folder.resolve("pirate.toml"), custom, StandardCharsets.UTF_8);
    repository.publish(LanguageLoopback.REVISION_TWO, "fi_FI", "[runtime]\nno_description_provided = \"Ei kuvausta saatavilla\"\n"
        + "[command.runtime]\nplayer_only = \"Vain pelaajille\"\n"
        + "missing_permission = \"Ei oikeutta {permission}\"\n");
    repository.revision(LanguageLoopback.REVISION_TWO);

    LanguageUpstreamSync.SyncReport report = AdaptLanguage.updateAction().update().get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertThat(report.successful()).isTrue();
    assertThat(report.revision()).contains(LanguageLoopback.REVISION_TWO);
    assertThat(report.changedLocales()).containsExactly("fi_FI");
    assertThat(report.merged().get("fi_FI").kept()).containsExactly(CommandRuntimeMessages.PLAYER_ONLY.id());
    String merged = Files.readString(finnish, StandardCharsets.UTF_8);
    assertThat(merged).contains("Oma muokkaus", "Ei kuvausta saatavilla", "Ei oikeutta {permission}")
        .doesNotContain("Vain pelaajille");
    assertThat(folder.resolve("pirate.toml")).hasContent(custom);
    assertThat(folder.resolve(".upstream/upstream.properties")).content()
        .contains("revision=" + LanguageLoopback.REVISION_TWO);
    LocalizationSnapshot pirate = AdaptLanguage.editorOptions().loader().load("pirate");
    assertThat(pirate.value(CommandRuntimeMessages.MISSING_PERMISSION)).isEqualTo(new TextValue("Ei oikeutta {permission}"));
  }
}
