package art.arcane.adapt.content.adaptation.architect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectTemporaryBlocksJournalTest {
  @Test
  void chunkLocalCoordinatesRoundTripAcrossNegativeWorldHeight() {
    int encoded = ArchitectTemporaryBlocks.encodeBlock(15, 319, 7, -64);

    assertThat(ArchitectTemporaryBlocks.decodeX(encoded)).isEqualTo(15);
    assertThat(ArchitectTemporaryBlocks.decodeZ(encoded)).isEqualTo(7);
    assertThat(ArchitectTemporaryBlocks.decodeY(encoded, -64)).isEqualTo(319);
  }

  @Test
  void encodingMasksCoordinatesToOneChunk() {
    int encoded = ArchitectTemporaryBlocks.encodeBlock(31, 64, 18, -64);

    assertThat(ArchitectTemporaryBlocks.decodeX(encoded)).isEqualTo(15);
    assertThat(ArchitectTemporaryBlocks.decodeZ(encoded)).isEqualTo(2);
    assertThat(ArchitectTemporaryBlocks.decodeY(encoded, -64)).isEqualTo(64);
  }
}
