package art.arcane.adapt.localization;

import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Built-in messages live under {@code adapt.} like every addon's under its namespace, so an addon whose namespace is a
 * built-in segment or skill name installs beside them and both texts resolve.
 */
class BuiltInNamespaceTest {
  @TempDir
  Path data;

  @Test
  void addonsNamedLikeBuiltInSegmentsInstallBesideTheBuiltInText() {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon(),
        new MessagesAddon("gui", "common.back", "Addon back"),
        new MessagesAddon("herbalism", "preferences.herbalismreplant.sneak", "Addon sneak"),
        new MessagesAddon("skill", "agility.name", "Addon agility"),
        new MessagesAddon("language", "menu.your_language", "Addon language"))) {
      assertThat(AdaptLanguage.texts().bundle("gui")).isPresent();
      assertThat(AdaptLanguage.texts().bundle("herbalism")).isPresent();
      assertThat(AdaptLanguage.texts().bundle("skill")).isPresent();
      assertThat(AdaptLanguage.texts().bundle("language")).isPresent();

      assertThat(AdaptLanguage.text(AdaptLanguage.key("gui.common.back"))).contains("Addon back");
      assertThat(AdaptLanguage.text(GuiMessages.BACK)).contains("Back").doesNotContain("Addon");
      assertThat(AdaptLanguage.text(AdaptLanguage.key("herbalism.preferences.herbalismreplant.sneak")))
          .contains("Addon sneak");
      assertThat(AdaptLanguage.text(AdaptLanguage.key("adapt.herbalism.preferences.herbalismreplant.sneak")))
          .contains("Require sneak to replant");
      assertThat(AdaptLanguage.text(AdaptLanguage.key("skill.agility.name"))).contains("Addon agility");
      assertThat(AdaptLanguage.text(AdaptLanguage.key("adapt.skill.agility.name"))).contains("Agility")
          .doesNotContain("Addon");
      assertThat(AdaptLanguage.text(AdaptLanguage.key("language.menu.your_language"))).contains("Addon language");
      assertThat(AdaptLanguage.text(BukkitLanguageMessages.YOUR_LANGUAGE)).doesNotContain("Addon");
      assertThat(AdaptLanguage.text(BukkitLanguageMessages.EDITOR_BACK)).isEqualTo(
          AdaptLanguage.text(AdaptLanguage.key("adapt." + BukkitLanguageMessages.EDITOR_BACK.id())));
      assertThat(AdaptLanguage.text(BukkitLanguageMessages.EDITOR_BACK)).doesNotContain("[missing:");
    }
  }

  private record MessagesAddon(String namespace, String key, String text) implements AdaptAddon {
    @Override
    public void register(ContentRegistrar registrar) {
      registrar.messages(MessageBundle.builder().message(key, text).build());
    }
  }
}
