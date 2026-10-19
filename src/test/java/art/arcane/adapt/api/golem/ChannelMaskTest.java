package art.arcane.adapt.api.golem;

import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelMaskTest {
  @Test
  void eachDyeOwnsOneDistinctBitWithinSixteen() {
    int seen = ChannelMask.NONE;
    for (DyeColor color : DyeColor.values()) {
      int bit = ChannelMask.bit(color);
      assertThat(Integer.bitCount(bit)).isEqualTo(1);
      assertThat(seen & bit).isZero();
      seen |= bit;
    }
    assertThat(seen).isEqualTo(ChannelMask.ALL).isEqualTo(0xFFFF);
    assertThat(ChannelMask.bit(DyeColor.WHITE)).isEqualTo(1);
    assertThat(ChannelMask.bit(DyeColor.BLACK)).isEqualTo(1 << 15);
  }

  @Test
  void zeroMeansNone() {
    assertThat(ChannelMask.isNone(ChannelMask.NONE)).isTrue();
    assertThat(ChannelMask.colors(ChannelMask.NONE)).isEmpty();
    assertThat(ChannelMask.contains(ChannelMask.NONE, DyeColor.RED)).isFalse();
    assertThat(ChannelMask.isNone(ChannelMask.of(DyeColor.RED))).isFalse();
  }

  @Test
  void withAndWithoutToggleSingleColors() {
    int mask = ChannelMask.with(ChannelMask.NONE, DyeColor.RED);
    mask = ChannelMask.with(mask, DyeColor.BLUE);
    mask = ChannelMask.with(mask, DyeColor.RED);

    assertThat(ChannelMask.colors(mask)).containsExactly(DyeColor.BLUE, DyeColor.RED);
    assertThat(ChannelMask.contains(mask, DyeColor.RED)).isTrue();

    mask = ChannelMask.without(mask, DyeColor.RED);
    assertThat(ChannelMask.colors(mask)).containsExactly(DyeColor.BLUE);
    assertThat(ChannelMask.without(mask, DyeColor.RED)).isEqualTo(mask);
    assertThat(ChannelMask.of(DyeColor.BLUE, DyeColor.RED)).isEqualTo(ChannelMask.with(mask, DyeColor.RED));
  }

  @Test
  void normalizeDropsBitsAboveSixteen() {
    assertThat(ChannelMask.normalize(0x1_0003)).isEqualTo(0x0003);
    assertThat(ChannelMask.normalize(-1)).isEqualTo(ChannelMask.ALL);
    assertThat(ChannelMask.colors(0x1_0001)).containsExactly(DyeColor.WHITE);
  }
}
