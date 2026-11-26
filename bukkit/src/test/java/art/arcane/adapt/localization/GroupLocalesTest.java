package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.GroupMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every locale names every adaptation group and the power a last unlearned level frees, keeping placeholders. */
class GroupLocalesTest extends AdaptTestBase {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");

  @Test
  void everyLocaleNamesEveryGroup() throws IOException {
    List<TextKey> keys = new ArrayList<>(GroupMessages.ALL);
    keys.add(GuiMessages.POWER_FREED);
    assertThat(VolmitLocales.nonEnglish()).hasSize(17);
    for (String locale : VolmitLocales.nonEnglish()) {
      Path file = RESOURCE_ROOT.resolve(locale + ".toml");
      LocaleOverlay overlay = AdaptLanguage.parseOverlay(file.toString(), locale, Files.readString(file));
      for (TextKey key : keys) {
        assertThat(overlay.value(key.id())).as("%s %s", locale, key.id()).isInstanceOf(TextValue.class);
        String translated = ((TextValue) overlay.value(key.id())).template();
        assertThat(translated).as("%s %s", locale, key.id()).isNotBlank();
        for (String placeholder : key.placeholders()) {
          assertThat(translated).as("%s %s", locale, key.id()).contains("{" + placeholder + "}");
        }
      }
    }
  }
}
