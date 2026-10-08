package art.arcane.adapt.localization;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;
import static org.assertj.core.api.Assertions.assertThat;

class AdaptLanguageColorTest {
  @ParameterizedTest
  @ValueSource(strings = {"&c", "§c", "&#123abc", "&x123abc", "[123abc]",
      "§x§1§2§3§a§b§c", "<red>", "<color:#123abc>", "<gradient:red:blue>",
      "<rainbow>", "&r", "§r", "<reset>"})
  void explicitTemplateColorsControlStyledArgumentsAndIgnoreAutomaticGradients(String color) {
    MessageArgs arguments = MessageArgs.builder().add(trusted("value", "&aValue&7")).build();

    String rendered = AdaptLanguage.renderTemplate(color + "Before {value} after", arguments, true);

    assertThat(ComponentText.markup("&e" + rendered).legacy())
        .isEqualTo(ComponentText.markup(color + "Before Value after").legacy());
  }

  @Test
  void templateGradientIncludesTheWholePlaceholderValue() {
    MessageArgs arguments = MessageArgs.builder().add(trusted("value", "&aLong value&7")).build();

    String rendered = AdaptLanguage.renderTemplate("<gradient:red:blue>Before {value} after</gradient>", arguments, false);

    assertThat(ComponentText.markup(rendered).legacy())
        .isEqualTo(ComponentText.markup("<gradient:red:blue>Before Long value after</gradient>").legacy());
  }

  @Test
  void plainTemplatesKeepTrustedArgumentStyling() {
    MessageArgs arguments = MessageArgs.builder().add(trusted("value", "&aValue&7")).build();

    assertThat(AdaptLanguage.renderTemplate("Before {value} after", arguments, false))
        .isEqualTo("Before §aValue after");
  }

  @Test
  void explicitColorDoesNotAllowUntrustedMarkupInjection() {
    MessageArgs arguments = MessageArgs.builder()
        .add(untrusted("value", "&a<red>§b[abcdef]"))
        .build();

    assertThat(ComponentText.markup(AdaptLanguage.renderTemplate("&c{value}", arguments, true)).plain())
        .isEqualTo("＆a＜red＞［abcdef］");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Plain", "&lBold", "<bold>Bold</bold>", "<color:invalid>Invalid",
      "\\<red>Escaped", "\\&cEscaped", "\\[123abc]Escaped"})
  void templatesWithoutValidExplicitColorKeepTheDefault(String template) {
    String rendered = AdaptLanguage.renderTemplate(template, MessageArgs.empty(), false, "§e");

    assertThat(rendered)
        .isEqualTo(ComponentText.markup("§e" + template).legacy());
  }

  @Test
  void partiallyColoredMultilineTemplatesSuppressTheDefaultFromTheStart() {
    String rendered = AdaptLanguage.renderTemplate("First\n&cRed\n&rReset", MessageArgs.empty(), true, "§e");

    assertThat(ComponentText.markup(rendered).legacy())
        .isEqualTo(ComponentText.markup("First\n&cRed\n&rReset").legacy());
  }

  @Test
  void automaticGradientsDoNotChangeTheJavaDefaultColor() {
    MessageArgs arguments = MessageArgs.builder().add(untrusted("value", "Long value")).build();

    String rendered = AdaptLanguage.renderTemplate("Before {value} after", arguments, true, "§e");

    assertThat(ComponentText.markup(rendered).plain()).isEqualTo("Before Long value after");
    assertThat(rendered).isEqualTo("§eBefore Long value after");
  }

  @Test
  void repeatedPlaceholdersNeverReprocessInsertedPlaceholderText() {
    MessageArgs arguments = MessageArgs.builder()
        .add(trusted("first", "{second}"))
        .add(trusted("second", "Replacement"))
        .build();

    String rendered = AdaptLanguage.renderTemplate("&c{first}|{first}|{second}", arguments, false);

    assertThat(ComponentText.markup(rendered).plain()).isEqualTo("{second}|{second}|Replacement");
  }
}
