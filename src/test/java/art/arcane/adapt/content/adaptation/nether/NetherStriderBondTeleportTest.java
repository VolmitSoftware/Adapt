package art.arcane.adapt.content.adaptation.nether;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NetherStriderBondTeleportTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/nether/NetherStriderBond.java");

  @Test
  void rescueRewardsOnlyASuccessfulOnlineTeleport() {
    assertThat(NetherStriderBond.shouldCommitRescue(true, null, true)).isTrue();
    assertThat(NetherStriderBond.shouldCommitRescue(false, null, true)).isFalse();
    assertThat(NetherStriderBond.shouldCommitRescue(null, null, true)).isFalse();
    assertThat(NetherStriderBond.shouldCommitRescue(true, new IllegalStateException(), true)).isFalse();
    assertThat(NetherStriderBond.shouldCommitRescue(true, null, false)).isFalse();
  }

  @Test
  void rescueUsesAsyncTeleportAndCommitsBackOnPlayerOwnership() throws Exception {
    String source = Files.readString(SOURCE);
    int teleport = source.indexOf("p.teleportAsync(safe, PlayerTeleportEvent.TeleportCause.PLUGIN)");
    int completion = source.indexOf("private void finishRescueTeleport");
    int ownership = source.indexOf("J.runEntity(p, () -> {", completion);
    int outcome = source.indexOf("shouldCommitRescue(success, failure, p.isOnline())", ownership);
    int reward = source.indexOf("addStat(p, \"nether.strider-bond.lava-rescues\"", outcome);

    assertThat(teleport).isGreaterThanOrEqualTo(0);
    assertThat(completion).isGreaterThan(teleport);
    assertThat(ownership).isGreaterThan(completion);
    assertThat(outcome).isGreaterThan(ownership);
    assertThat(reward).isGreaterThan(outcome);
    assertThat(source).doesNotContain("p.teleport(safe)");
  }
}
