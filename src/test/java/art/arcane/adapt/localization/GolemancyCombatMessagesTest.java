package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
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
  private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
  private static final List<TextKey> COMBAT_KEYS = List.of(
      GolemancyMessages.GUI_COMBAT_ON,
      GolemancyMessages.GUI_COMBAT_OFF,
      GolemancyMessages.GUI_COMBAT_LOCKED,
      GolemancyMessages.GUI_COMBAT_ON_LORE,
      GolemancyMessages.GUI_COMBAT_OFF_LORE,
      GolemancyMessages.GUI_COMBAT_LOCKED_LORE,
      GolemancyMessages.OVERSEER_SIC_IGNORED);

  @Test
  void theIgnoredSicNoteNamesTheCountOfGolems() {
    assertThat(GolemancyMessages.OVERSEER_SIC_IGNORED.placeholders()).containsExactly("count");
    assertThat(AdaptLanguage.text(GolemancyMessages.OVERSEER_SIC_IGNORED, trusted("count", 3)))
        .isEqualTo("Golems with combat off ignored Sic: 3");
    assertThatThrownBy(() -> AdaptLanguage.text(GolemancyMessages.OVERSEER_SIC_IGNORED))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing=[count]");
  }

  @Test
  void theToggleTextsTakeNoPlaceholders() {
    for (TextKey key : COMBAT_KEYS.subList(0, COMBAT_KEYS.size() - 1)) {
      assertThat(key.placeholders()).as(key.id()).isEmpty();
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
}
