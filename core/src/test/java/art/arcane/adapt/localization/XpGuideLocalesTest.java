package art.arcane.adapt.localization;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.localization.catalog.XpGuideMessages;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Every locale translates every guide line a registered skill shows, the mutation's rules and the kept and lent states. */
class XpGuideLocalesTest {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");

  @TempDir
  Path data;

  @Test
  void everyLocaleTranslatesEveryGuideLine() throws IOException {
    Set<TextKey> keys = new LinkedHashSet<>(List.of(XpGuideMessages.TITLE, XpGuideMessages.MUTATION,
        XpGuideMessages.ADAPTATIONS, MutatedMessages.KEPT_STATE, MutatedMessages.LENT_STATE));
    keys.addAll(XpGuideMessages.rules());
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon())) {
      for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getSkills()) {
        for (Text line : skill.getXpGuide()) {
          String id = line.key();
          MessageKey declared = AdaptLanguage.builtInCatalog().key(id);
          assertThat(declared).as("%s %s", skill.getId(), line.key()).isInstanceOf(TextKey.class);
          keys.add((TextKey) declared);
        }
      }
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
