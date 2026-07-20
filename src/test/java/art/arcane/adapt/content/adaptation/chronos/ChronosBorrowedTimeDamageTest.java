package art.arcane.adapt.content.adaptation.chronos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChronosBorrowedTimeDamageTest {
  @Test
  void paybackDamageCannotDeferItselfAndRestoresNestedState() {
    ChronosBorrowedTime borrowedTime = new ChronosBorrowedTime();

    assertThat(borrowedTime.isApplyingPaybackDamage()).isFalse();
    borrowedTime.withPaybackDamage(() -> {
      assertThat(borrowedTime.isApplyingPaybackDamage()).isTrue();
      borrowedTime.withPaybackDamage(() -> assertThat(borrowedTime.isApplyingPaybackDamage()).isTrue());
      assertThat(borrowedTime.isApplyingPaybackDamage()).isTrue();
    });
    assertThat(borrowedTime.isApplyingPaybackDamage()).isFalse();
  }
}
