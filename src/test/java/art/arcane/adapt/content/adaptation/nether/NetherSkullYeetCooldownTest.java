package art.arcane.adapt.content.adaptation.nether;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetherSkullYeetCooldownTest {
  @Test
  void cooldownScalesDownPerLevelFromDefaults() {
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 1)).isEqualTo(10);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 2)).isEqualTo(5);
  }

  @Test
  void cooldownNeverDropsBelowOneSecondAtOrBeyondMaxLevel() {
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 3)).isEqualTo(1);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 4)).isEqualTo(1);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 100)).isEqualTo(1);
  }
}
