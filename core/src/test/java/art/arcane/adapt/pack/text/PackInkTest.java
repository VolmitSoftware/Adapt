package art.arcane.adapt.pack.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackInkTest {
  private static final int[] CHAT = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF,
      0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

  @Test
  void everyChatColourBecomesAnInkThatReadsOnTheDarkestPaper() {
    for (int colour : CHAT) {
      assertThat(PackInk.contrast(PackInk.of(colour), PackInk.PAPER)).as(Integer.toHexString(colour)).isGreaterThanOrEqualTo(4.5);
    }
  }

  @Test
  void chatColourCodesBecomeTheirInksAndHexColoursStay() {
    String inked = PackInk.inked("§a+ 25% §7Max Damage §x§2§e§2§2§1§6ink§l!");
    assertThat(inked).doesNotContain("§a+").doesNotContain("§7Max").contains("§x§2§e§2§2§1§6ink").contains("§l!");
    for (int code = 0; code < 16; code++) {
      String one = PackInk.inked("§" + "0123456789abcdef".charAt(code) + "x");
      int rgb = Integer.parseInt(one.substring(2, 14).replace("§", ""), 16);
      assertThat(PackInk.contrast(rgb, PackInk.PAPER)).as("code %d", code).isGreaterThanOrEqualTo(4.5);
    }
  }

  @Test
  void aLightColourStaysLighterThanTheDarkColourOfTheSameHue() {
    assertThat(PackInk.contrast(PackInk.of(0x55FF55), PackInk.PAPER)).isLessThan(PackInk.contrast(PackInk.of(0x00AA00), PackInk.PAPER));
  }
}
