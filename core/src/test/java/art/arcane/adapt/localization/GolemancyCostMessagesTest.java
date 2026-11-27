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

class GolemancyCostMessagesTest extends AdaptTestBase {
  private static final Path RESOURCE_ROOT = Path.of("../src/main/resources");
  private static final List<TextKey> COST_KEYS = List.of(
      key("golemancy.plinth.levels"),
      key("golemancy.binding.levels"),
      key("golemancy.plinth.preview"),
      key("golemancy.plinth.preview_short"),
      key("golemancy.plinth.preview_free"));

  @Test
  void theRefusalsNameTheCostAndTheLevelsYouHave() {
    assertThat(key("golemancy.plinth.levels").placeholders()).containsExactlyInAnyOrder("levels", "have");
    assertThat(key("golemancy.binding.levels").placeholders()).containsExactlyInAnyOrder("levels", "have");
    assertThat(AdaptLanguage.text(key("golemancy.plinth.levels"), trusted("levels", 5), trusted("have", 3)))
        .isEqualTo("Not enough experience: this infusion costs 5 levels and you have 3");
    assertThat(AdaptLanguage.text(key("golemancy.binding.levels"), trusted("levels", 2), trusted("have", 0)))
        .isEqualTo("Not enough experience: binding this golem costs 2 levels and you have 0");
  }

  @Test
  void eachPreviewTakesOnlyThePlaceholdersItShows() {
    assertThat(key("golemancy.plinth.preview").placeholders()).containsExactlyInAnyOrder("result", "levels");
    assertThat(key("golemancy.plinth.preview_short").placeholders()).containsExactlyInAnyOrder("result", "levels", "have");
    assertThat(key("golemancy.plinth.preview_free").placeholders()).containsExactly("result");
  }

  @Test
  void aCallThatLeavesOutOrAddsAnArgumentIsRejected() {
    assertThatThrownBy(() -> AdaptLanguage.text(key("golemancy.plinth.levels"), trusted("levels", 5)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing=[have]");
    assertThatThrownBy(() -> AdaptLanguage.text(key("golemancy.binding.levels"), trusted("levels", 2)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing=[have]");
    assertThatThrownBy(() -> AdaptLanguage.text(key("golemancy.plinth.preview_free"), trusted("result", "Rune"), trusted("levels", 2)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unexpected=[levels]");
    assertThatThrownBy(() -> AdaptLanguage.text(key("golemancy.plinth.preview"), trusted("result", "Rune"), trusted("levels", 2), trusted("have", 1)))
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

  private static TextKey key(String id) {
    return (TextKey) BuiltInText.catalog().key(AdaptKeys.id(id));
  }
}
