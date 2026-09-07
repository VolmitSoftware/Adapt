package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HunterActivationOutcomeTest {
  @Test
  void trophyAndHeadChancesAreIndependent() {
    assertThat(HunterTrophySkinner.decideDrops(0.8D, 0.01D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophySkinner.DropOutcome(false, true));
    assertThat(HunterTrophySkinner.decideDrops(0.1D, 0.8D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophySkinner.DropOutcome(true, false));
    assertThat(HunterTrophySkinner.decideDrops(0.1D, 0.01D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophySkinner.DropOutcome(true, true));
  }
}
