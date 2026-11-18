package art.arcane.adapt.content.item;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hover width is bounded structurally: every locale's text is wrapped before it
 * becomes lore, so a long translation can never blow the tooltip out sideways.
 */
class BackpackLoreWrapTest {
  @Test
  void shortTextPassesThroughAsOneLine() {
    assertThat(BackpackItem.wrap("Right-click to open", 38))
        .containsExactly("Right-click to open");
  }

  @Test
  void longTextWrapsAtWordBoundariesWithinTheWidth() {
    String text = "Craft it alone in a crafting grid to switch storage mode; it must be empty first";
    List<String> lines = BackpackItem.wrap(text, 38);

    assertThat(lines).hasSizeGreaterThan(1);
    for (String line : lines) {
      assertThat(line.length()).isLessThanOrEqualTo(38);
    }
    assertThat(String.join(" ", lines)).isEqualTo(text);
  }

  @Test
  void aSingleOverlongWordStaysOnItsOwnLineInsteadOfBeingDropped() {
    String word = "Unzerbrechlichkeitsverzauberungsschutz";
    assertThat(BackpackItem.wrap("a " + word + " b", 10))
        .containsExactly("a", word, "b");
  }

  @Test
  void nullAndBlankProduceNoLoreLines() {
    assertThat(BackpackItem.wrap(null, 38)).isEmpty();
    assertThat(BackpackItem.wrap("   ", 38)).isEmpty();
  }
}
