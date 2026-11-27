package art.arcane.adapt.api.text;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextKeysTest {
  @Test
  void configFieldKeysFollowTheComponentKindAndId() {
    assertThat(TextKeys.skillConfigField(Id.adapt("agility"), "enabled"))
        .isEqualTo("adapt.config.fields.skills.agility.enabled");
    assertThat(TextKeys.adaptationConfigField(Id.of("alchemist", "alchemy/transmute"), "rewards.base"))
        .isEqualTo("alchemist.config.fields.adaptations.alchemy.transmute.rewards.base");
  }

  @Test
  void configFieldKeysAreValidMessageKeys() {
    String key = TextKeys.skillConfigField(Id.of("alchemist", "alchemy"), "brewXp");

    assertThat(Text.key(key + ".label").key()).isEqualTo("alchemist.config.fields.skills.alchemy.brewXp.label");
  }

  @Test
  void aMalformedFieldPathIsRefused() {
    for (String field : new String[] {"", ".x", "x.", "a..b"}) {
      assertThatThrownBy(() -> TextKeys.skillConfigField(Id.adapt("agility"), field))
          .as(field).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
