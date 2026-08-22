package art.arcane.adapt.api.fx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FxBudgetTest {
  @Test
  void globalPacketBudgetIsAHardCeilingForGameplayEffects() {
    FxBudget.resetTick();

    assertThat(FxBudget.tryConsume(FxPriority.GAMEPLAY, FxBudget.GLOBAL_PACKET_BUDGET))
        .isEqualTo(FxBudget.GLOBAL_PACKET_BUDGET);
    assertThat(FxBudget.tryConsume(FxPriority.GAMEPLAY, 1)).isZero();
  }
}
