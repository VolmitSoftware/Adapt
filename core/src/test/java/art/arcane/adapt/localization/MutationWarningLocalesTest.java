package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Every locale tells the player a mutation cannot be undone in its own words. */
class MutationWarningLocalesTest extends AdaptTestBase {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");

  @Test
  void everyLocaleTranslatesThePermanenceWarning() throws IOException {
    assertThat(VolmitLocales.nonEnglish()).hasSize(17);
    for (String locale : VolmitLocales.nonEnglish()) {
      Path file = RESOURCE_ROOT.resolve(locale + ".toml");
      LocaleOverlay overlay = AdaptLanguage.parseOverlay(file.toString(), locale, Files.readString(file));
      String id = MutationMessages.PERMANENT.id();
      assertThat(overlay.value(id)).as("%s %s", locale, id).isInstanceOf(TextValue.class);
      assertThat(((TextValue) overlay.value(id)).template()).as("%s %s", locale, id)
          .isNotBlank()
          .isNotEqualTo("This cannot be undone");
    }
  }
}
