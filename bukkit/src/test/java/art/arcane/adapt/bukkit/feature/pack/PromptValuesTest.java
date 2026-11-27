package art.arcane.adapt.bukkit.feature.pack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptValuesTest {
  @Test
  void numbersParseAndClamp() {
    assertThat(PromptValues.number(" 12.5 ", null, null)).isEqualTo(12.5D);
    assertThat(PromptValues.number("300", 0.0D, 100.0D)).isEqualTo(100.0D);
    assertThat(PromptValues.number("-4", 0.0D, null)).isEqualTo(0.0D);
    assertThat(PromptValues.number("twelve", null, null)).isNull();
    assertThat(PromptValues.number("NaN", null, null)).isNull();
  }

  @Test
  void choicesMatchByNumberOrText() {
    List<String> options = List.of("Red", "Green", "Blue");
    assertThat(PromptValues.choice("2", options)).isEqualTo(1);
    assertThat(PromptValues.choice(" blue ", options)).isEqualTo(2);
    assertThat(PromptValues.choice("4", options)).isEqualTo(-1);
    assertThat(PromptValues.choice("purple", options)).isEqualTo(-1);
  }

  @Test
  void textIsClippedAndCancelIsRecognised() {
    assertThat(PromptValues.clip("abcdef", 3)).isEqualTo("abc");
    assertThat(PromptValues.clip("abcdef", 0)).isEqualTo("abcdef");
    assertThat(PromptValues.cancels(" Cancel ")).isTrue();
    assertThat(PromptValues.cancels("cancelled")).isFalse();
  }
}
