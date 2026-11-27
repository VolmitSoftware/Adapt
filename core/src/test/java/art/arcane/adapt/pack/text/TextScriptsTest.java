package art.arcane.adapt.pack.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextScriptsTest {
  @Test
  void bitmapScriptsMoveAndUnifontScriptsDoNot() {
    assertThat(TextScripts.movable("Wall Jump: +2, 50% / ±")).isTrue();
    assertThat(TextScripts.movable("Ловкость")).isTrue();
    assertThat(TextScripts.movable("Ἀγιλότης")).isTrue();
    assertThat(TextScripts.movable("Éàüß")).isTrue();
    assertThat(TextScripts.movable("敏捷")).isFalse();
    assertThat(TextScripts.movable("민첩")).isFalse();
    assertThat(TextScripts.movable("すばやさ")).isFalse();
    assertThat(TextScripts.movable("Agility「」")).isFalse();
    assertThat(TextScripts.movable("זריזות")).isFalse();
  }
}
