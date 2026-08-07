package art.arcane.adapt.util.common.compat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaperCompatItemFramingTest {
  @Test
  void framedPayloadsRoundTripThroughTheirFormatTag() {
    byte[] payload = new byte[]{7, -3, 0, 42};

    byte[] framed = PaperCompat.frameItems(PaperCompat.ITEMS_FORMAT_NBT, payload);

    assertThat(framed).hasSize(payload.length + 1);
    assertThat(PaperCompat.itemsFormat(framed)).isEqualTo(PaperCompat.ITEMS_FORMAT_NBT);
    assertThat(PaperCompat.itemsPayload(framed)).containsExactly(payload);
  }

  @Test
  void emptyAndMissingPayloadsStayDistinguishable() {
    byte[] framed = PaperCompat.frameItems(PaperCompat.ITEMS_FORMAT_STREAM, new byte[0]);

    assertThat(PaperCompat.itemsFormat(framed)).isEqualTo(PaperCompat.ITEMS_FORMAT_STREAM);
    assertThat(PaperCompat.itemsPayload(framed)).isEmpty();
    assertThat(PaperCompat.itemsFormat(new byte[0])).isEqualTo((byte) -1);
    assertThat(PaperCompat.itemsFormat(null)).isEqualTo((byte) -1);
    assertThat(PaperCompat.itemsPayload(null)).isEmpty();
  }

  @Test
  void aNullPayloadFramesAsAnEmptyBodyRatherThanThrowing() {
    byte[] framed = PaperCompat.frameItems(PaperCompat.ITEMS_FORMAT_NBT, null);

    assertThat(framed).containsExactly(PaperCompat.ITEMS_FORMAT_NBT);
  }
}
