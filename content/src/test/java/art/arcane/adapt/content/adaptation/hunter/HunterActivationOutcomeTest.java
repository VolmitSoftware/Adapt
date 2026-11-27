package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HunterActivationOutcomeTest {
  @Test
  void trophyAndHeadChancesAreIndependent() {
    assertThat(HunterTrophies.decideDrops(0.8D, 0.01D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophies.DropOutcome(false, true));
    assertThat(HunterTrophies.decideDrops(0.1D, 0.8D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophies.DropOutcome(true, false));
    assertThat(HunterTrophies.decideDrops(0.1D, 0.01D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophies.DropOutcome(true, true));
  }
}
