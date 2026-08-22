package art.arcane.adapt.content.adaptation.architect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectFoundationJournalTest {
  @Test
  void chunkLocalCoordinatesRoundTripAcrossNegativeWorldHeight() {
    int encoded = ArchitectFoundation.encodeBlock(15, 319, 7, -64);

    assertThat(ArchitectFoundation.decodeX(encoded)).isEqualTo(15);
    assertThat(ArchitectFoundation.decodeZ(encoded)).isEqualTo(7);
    assertThat(ArchitectFoundation.decodeY(encoded, -64)).isEqualTo(319);
  }

  @Test
  void encodingMasksCoordinatesToOneChunk() {
    int encoded = ArchitectFoundation.encodeBlock(31, 64, 18, -64);

    assertThat(ArchitectFoundation.decodeX(encoded)).isEqualTo(15);
    assertThat(ArchitectFoundation.decodeZ(encoded)).isEqualTo(2);
    assertThat(ArchitectFoundation.decodeY(encoded, -64)).isEqualTo(64);
  }
}
