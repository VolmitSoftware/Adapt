package art.arcane.adapt.content.adaptation.enchanting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnchantingRecoveryTransactionTest {
  @Test
  void offerRerollAcceptsEitherASettledProviderOrAConsumedDefaultCost() {
    assertThat(EnchantingRerolls.acceptsDeferredSettlement(true, false)).isTrue();
    assertThat(EnchantingRerolls.acceptsDeferredSettlement(false, true)).isTrue();
    assertThat(EnchantingRerolls.acceptsDeferredSettlement(false, false)).isFalse();
  }
}
