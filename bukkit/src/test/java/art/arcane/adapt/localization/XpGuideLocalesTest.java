package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.localization.catalog.XpGuideMessages;
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

/** Every locale translates every XP guide line, the mutation's rules and the kept and lent states, keeping placeholders. */
class XpGuideLocalesTest extends AdaptTestBase {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");

  @Test
  void everyLocaleTranslatesEveryGuideLine() throws IOException {
    List<TextKey> keys = new ArrayList<>(List.of(XpGuideMessages.TITLE, XpGuideMessages.MUTATION, MutatedMessages.KEPT_STATE,
        MutatedMessages.LENT_STATE));
    keys.addAll(XpGuideMessages.rules());
    for (String skill : XpGuideMessages.skills()) {
      keys.addAll(XpGuideMessages.lines(skill));
    }
    assertThat(VolmitLocales.nonEnglish()).hasSize(17);
    for (String locale : VolmitLocales.nonEnglish()) {
      LocaleOverlay overlay = overlay(locale);
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

  private static LocaleOverlay overlay(String locale) throws IOException {
    Path file = RESOURCE_ROOT.resolve(locale + ".toml");
    return AdaptLanguage.parseOverlay(file.toString(), locale, Files.readString(file));
  }
}
