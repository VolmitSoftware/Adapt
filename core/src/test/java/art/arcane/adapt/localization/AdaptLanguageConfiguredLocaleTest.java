package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptLanguageConfiguredLocaleTest extends AdaptTestBase {
  @Test
  void unchangedConfiguredLocaleIsNotReparsed() throws Exception {
    assertThat(AdaptLanguage.reloadPassive()).isTrue();
    String loaded = AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED);
    Path file = AdaptLanguage.languageFolder().toPath().resolve(AdaptLanguage.activeLocale() + ".toml");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "[adapt.runtime]\nno_description_provided = \"Edited on disk\"\n");

    assertThat(AdaptLanguage.reloadConfiguredLocale()).isFalse();
    assertThat(AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).isEqualTo(loaded);

    assertThat(AdaptLanguage.reloadPassive()).isTrue();
    assertThat(AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED)).contains("Edited on disk");
  }
}
