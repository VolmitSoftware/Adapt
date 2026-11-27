package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GolemancyCombatMessagesTest extends AdaptTestBase {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");
  private static final List<TextKey> COMBAT_KEYS = List.of(
      key("golemancy.gui.combat_on"),
      key("golemancy.gui.combat_off"),
      key("golemancy.gui.combat_locked"),
      key("golemancy.gui.combat_on_lore"),
      key("golemancy.gui.combat_off_lore"),
      key("golemancy.gui.combat_locked_lore"),
      key("golemancy.overseer.sic_ignored"));

  @Test
  void theIgnoredSicNoteNamesTheCountOfGolems() {
    assertThat(key("golemancy.overseer.sic_ignored").placeholders()).containsExactly("count");
    assertThat(AdaptLanguage.text(key("golemancy.overseer.sic_ignored"), trusted("count", 3)))
        .isEqualTo("Golems with combat off ignored Sic: 3");
    assertThatThrownBy(() -> AdaptLanguage.text(key("golemancy.overseer.sic_ignored")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing=[count]");
  }

  @Test
  void theToggleTextsTakeNoPlaceholders() {
    for (TextKey key : COMBAT_KEYS.subList(0, COMBAT_KEYS.size() - 1)) {
      assertThat(key.placeholders()).as(key.id().toString()).isEmpty();
    }
  }

  @Test
  void everyLocaleTranslatesTheCombatMessagesWithTheSamePlaceholders() throws IOException {
    for (String locale : VolmitLocales.nonEnglish()) {
      Path file = RESOURCE_ROOT.resolve(locale + ".toml");
      LocaleOverlay overlay = AdaptLanguage.parseOverlay(file.toString(), locale, Files.readString(file));
      for (TextKey key : COMBAT_KEYS) {
        MessageValue translated = overlay.value(key.id());
        assertThat(translated).as("%s in %s", key.id(), locale).isNotNull();
        Set<String> placeholders = translated.placeholders();
        assertThat(placeholders).as("%s in %s", key.id(), locale).isEqualTo(key.placeholders());
      }
    }
  }

  private static TextKey key(String id) {
    return (TextKey) BuiltInText.catalog().key(AdaptKeys.id(id));
  }
}
