package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.data.unit.PlacementStamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XpProvenanceTest {
  @Test
  void permanentPlacementEvidenceDoesNotExpireWithTheXpTtl() {
    assertThat(XpProvenance.hasPermanentPlacementRecord(null)).isFalse();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(0, 0, 0))).isFalse();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(1, 0, 0))).isTrue();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(0, 1, 0))).isTrue();
  }
}
