package art.arcane.adapt.content.adaptation.enchanting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnchantingRecoveryTransactionTest {
  @Test
  void offerRerollAcceptsEitherASettledProviderOrAConsumedDefaultCost() {
    assertThat(EnchantingOfferReroll.acceptsDeferredSettlement(true, false)).isTrue();
    assertThat(EnchantingOfferReroll.acceptsDeferredSettlement(false, true)).isTrue();
    assertThat(EnchantingOfferReroll.acceptsDeferredSettlement(false, false)).isFalse();
  }
}
