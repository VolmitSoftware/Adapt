package art.arcane.adapt.content.adaptation.nether;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetherStriderBondTeleportTest {
  @Test
  void rescueRewardsOnlyASuccessfulOnlineTeleport() {
    assertThat(NetherStriderBond.shouldCommitRescue(true, null, true)).isTrue();
    assertThat(NetherStriderBond.shouldCommitRescue(false, null, true)).isFalse();
    assertThat(NetherStriderBond.shouldCommitRescue(null, null, true)).isFalse();
    assertThat(NetherStriderBond.shouldCommitRescue(true, new IllegalStateException(), true)).isFalse();
    assertThat(NetherStriderBond.shouldCommitRescue(true, null, false)).isFalse();
  }
}
