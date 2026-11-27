package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismSeedSowerTransactionTest {
  @Test
  void plantingAcceptsAConsumedDefaultOrASettledSuppressedProvider() {
    assertThat(HerbalismSowing.acceptsPlantingSettlement(true, false, false)).isTrue();
    assertThat(HerbalismSowing.acceptsPlantingSettlement(true, false, true)).isTrue();
    assertThat(HerbalismSowing.acceptsPlantingSettlement(false, true, true)).isTrue();
    assertThat(HerbalismSowing.acceptsPlantingSettlement(false, true, false)).isFalse();
    assertThat(HerbalismSowing.acceptsPlantingSettlement(false, false, true)).isFalse();
  }
}
