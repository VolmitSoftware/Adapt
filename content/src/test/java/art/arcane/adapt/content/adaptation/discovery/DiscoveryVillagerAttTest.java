package art.arcane.adapt.content.adaptation.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryVillagerAttTest {
  @Test
  void chanceAndXpCostCannotEscapeGameplayBounds() {
    assertThat(DiscoveryVillagerAtt.effectiveness(100D, 0.005D, 1D)).isEqualTo(1D);
    assertThat(DiscoveryVillagerAtt.effectiveness(-1D, 0.5D, 1D)).isZero();
    assertThat(DiscoveryVillagerAtt.xpCost(10, 1D, 2, 10D)).isEqualTo(1);
  }
}
