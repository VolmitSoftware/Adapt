package art.arcane.adapt.localization;

import art.arcane.adapt.api.content.AdaptationGroup;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.localization.catalog.GuiMessages;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Every locale names every group a registered skill shows and the power a last unlearned level frees, keeping placeholders. */
class GroupLocalesTest {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");

  @TempDir
  Path data;

  @Test
  void everyLocaleNamesEveryGroup() throws IOException {
    Set<TextKey> keys = new LinkedHashSet<>();
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon())) {
      for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getSkills()) {
        for (AdaptationGroup group : skill.getAdaptationGroups()) {
          String id = group.label().key();
          MessageKey key = AdaptLanguage.builtInCatalog().key(id);
          assertThat(key).as("%s %s", skill.getId(), id).isInstanceOf(TextKey.class);
          keys.add((TextKey) key);
        }
      }
    }
    assertThat(keys).isNotEmpty();
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
