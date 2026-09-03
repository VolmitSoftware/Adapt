package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptLanguageDownloadTest extends AdaptTestBase {

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
  void verifiedCacheRejectsModifiedBytes() throws Exception {
    Path target = AdaptLanguage.remote().cacheFile("de_DE");
    Files.createDirectories(target.getParent());
    Files.copy(Path.of("src/main/resources/de_DE.toml"), target);

    assertThat(AdaptLanguage.remote().read("de_DE", (locale, raw) -> AdaptLanguage.parseOverlay("cache", locale, raw)).state())
        .isEqualTo(RemoteLanguageCatalog.CacheState.VALID);
    Files.writeString(target, "modified", StandardCharsets.UTF_8);
    assertThat(AdaptLanguage.remote().read("de_DE", (locale, raw) -> AdaptLanguage.parseOverlay("cache", locale, raw)).state())
        .isEqualTo(RemoteLanguageCatalog.CacheState.INVALID);
  }
}
