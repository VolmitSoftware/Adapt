package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TamingLastBreathTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/taming/TamingLastBreath.java");

  @Test
  void higherLevelsShortenThePerPetCooldown() {
    assertThat(TamingLastBreath.cooldownMillis(0.0, 300000, 180000, 60000)).isEqualTo(300000L);
    assertThat(TamingLastBreath.cooldownMillis(1.0, 300000, 180000, 60000)).isEqualTo(120000L);
  }

  @Test
  void cooldownNeverFallsBelowTheConfiguredFloor() {
    assertThat(TamingLastBreath.cooldownMillis(1.0, 300000, 300000, 60000)).isEqualTo(60000L);
  }

  @Test
  void recallArrivalRequiresConfirmedTeleportSuccess() {
    assertThat(TamingLastBreath.successfulRecallTeleport(true, null)).isTrue();
    assertThat(TamingLastBreath.successfulRecallTeleport(false, null)).isFalse();
    assertThat(TamingLastBreath.successfulRecallTeleport(null, null)).isFalse();
    assertThat(TamingLastBreath.successfulRecallTeleport(true, new IllegalStateException())).isFalse();
  }

  @Test
  void saveRewardIsIndependentWhileArrivalEffectsWaitForCompletion() throws Exception {
    String source = Files.readString(SOURCE);
    int reward = source.indexOf("addStat(owner, \"taming.last-breath.saves\"");
    int teleport = source.indexOf("PaperCompat.teleportAsync(pet, destination)");
    int completion = source.indexOf("private void completeRecallTeleport");
    int outcome = source.indexOf("successfulRecallTeleport(success, failure)", completion);
    int arrival = source.indexOf("private void showRecallArrivalOwned", outcome);
    int effect = source.indexOf("fx(destination, FxPriority.TRANSITION)", arrival);

    assertThat(reward).isGreaterThanOrEqualTo(0);
    assertThat(teleport).isGreaterThan(reward);
    assertThat(completion).isGreaterThan(teleport);
    assertThat(outcome).isGreaterThan(completion);
    assertThat(arrival).isGreaterThan(outcome);
    assertThat(effect).isGreaterThan(arrival);
    assertThat(source).doesNotContain("J.teleport(pet, safe)");
  }
}
