package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismSeedSowerTransactionTest {
  @Test
  void plantingAcceptsAConsumedDefaultOrASettledSuppressedProvider() {
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(true, false, false)).isTrue();
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(true, false, true)).isTrue();
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(false, true, true)).isTrue();
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(false, true, false)).isFalse();
    assertThat(HerbalismSeedSower.acceptsPlantingSettlement(false, false, true)).isFalse();
  }
}
