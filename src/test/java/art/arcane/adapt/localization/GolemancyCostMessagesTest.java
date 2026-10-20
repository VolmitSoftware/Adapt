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

class GolemancyCostMessagesTest extends AdaptTestBase {
  private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
  private static final List<TextKey> COST_KEYS = List.of(
      GolemancyMessages.PLINTH_LEVELS,
      GolemancyMessages.BINDING_LEVELS,
      GolemancyMessages.PLINTH_PREVIEW,
      GolemancyMessages.PLINTH_PREVIEW_SHORT,
      GolemancyMessages.PLINTH_PREVIEW_FREE);

  @Test
  void theRefusalsNameTheCostAndTheLevelsYouHave() {
    assertThat(GolemancyMessages.PLINTH_LEVELS.placeholders()).containsExactlyInAnyOrder("levels", "have");
    assertThat(GolemancyMessages.BINDING_LEVELS.placeholders()).containsExactlyInAnyOrder("levels", "have");
    assertThat(AdaptLanguage.text(GolemancyMessages.PLINTH_LEVELS, trusted("levels", 5), trusted("have", 3)))
        .isEqualTo("Not enough experience: this infusion costs 5 levels and you have 3");
    assertThat(AdaptLanguage.text(GolemancyMessages.BINDING_LEVELS, trusted("levels", 2), trusted("have", 0)))
        .isEqualTo("Not enough experience: binding this golem costs 2 levels and you have 0");
  }

  @Test
  void eachPreviewTakesOnlyThePlaceholdersItShows() {
    assertThat(GolemancyMessages.PLINTH_PREVIEW.placeholders()).containsExactlyInAnyOrder("result", "levels");
    assertThat(GolemancyMessages.PLINTH_PREVIEW_SHORT.placeholders()).containsExactlyInAnyOrder("result", "levels", "have");
    assertThat(GolemancyMessages.PLINTH_PREVIEW_FREE.placeholders()).containsExactly("result");
  }

  @Test
  void aCallThatLeavesOutOrAddsAnArgumentIsRejected() {
    assertThatThrownBy(() -> AdaptLanguage.text(GolemancyMessages.PLINTH_LEVELS, trusted("levels", 5)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing=[have]");
    assertThatThrownBy(() -> AdaptLanguage.text(GolemancyMessages.BINDING_LEVELS, trusted("levels", 2)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing=[have]");
    assertThatThrownBy(() -> AdaptLanguage.text(GolemancyMessages.PLINTH_PREVIEW_FREE, trusted("result", "Rune"), trusted("levels", 2)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unexpected=[levels]");
    assertThatThrownBy(() -> AdaptLanguage.text(GolemancyMessages.PLINTH_PREVIEW, trusted("result", "Rune"), trusted("levels", 2), trusted("have", 1)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unexpected=[have]");
  }

  @Test
  void everyLocaleKeepsTheSamePlaceholdersForTheCostMessages() throws IOException {
    for (String locale : VolmitLocales.nonEnglish()) {
      Path file = RESOURCE_ROOT.resolve(locale + ".toml");
      LocaleOverlay overlay = AdaptLanguage.parseOverlay(file.toString(), locale, Files.readString(file));
      for (TextKey key : COST_KEYS) {
        MessageValue translated = overlay.value(key.id());
        assertThat(translated).as("%s in %s", key.id(), locale).isNotNull();
        Set<String> placeholders = translated.placeholders();
        assertThat(placeholders).as("%s in %s", key.id(), locale).isEqualTo(key.placeholders());
      }
    }
  }
}
