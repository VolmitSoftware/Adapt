package art.arcane.adapt.pack.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyTextTest {
  @Test
  void colourCodesSplitRunsAndResetReturnsToTheDefault() {
    assertThat(LegacyText.segments("plain §cred§r back", 0x404040)).containsExactly(
        new LegacyText.Segment("plain ", 0x404040), new LegacyText.Segment("red", 0xFF5555),
        new LegacyText.Segment(" back", 0x404040));
  }

  @Test
  void hexSequencesSetTheExactColour() {
    assertThat(LegacyText.segments("§x§1§2§a§b§C§dhex", 0)).containsExactly(new LegacyText.Segment("hex", 0x12ABCD));
  }

  @Test
  void formattingCodesAreDroppedWithoutSplitting() {
    assertThat(LegacyText.segments("§lbold §oitalic", 7)).containsExactly(new LegacyText.Segment("bold italic", 7));
    assertThat(LegacyText.plain("§a§lGo§r!")).isEqualTo("Go!");
  }

  @Test
  void aTrailingSectionSignIsKeptAsText() {
    assertThat(LegacyText.segments("a§", 0)).isEqualTo(List.of(new LegacyText.Segment("a§", 0)));
  }
}
