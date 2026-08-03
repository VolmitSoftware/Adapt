package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SeaborneTridentMasteryTeleportTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/seaborrne/SeaborneTridentMastery.java");

  @Test
  void releaseContinuesOnlyAfterConfirmedTeleportSuccess() {
    assertThat(SeaborneTridentMastery.successfulReleaseTeleport(true, null)).isTrue();
    assertThat(SeaborneTridentMastery.successfulReleaseTeleport(false, null)).isFalse();
    assertThat(SeaborneTridentMastery.successfulReleaseTeleport(null, null)).isFalse();
    assertThat(SeaborneTridentMastery.successfulReleaseTeleport(true, new IllegalStateException())).isFalse();
  }

  @Test
  void stuckTridentDefersVelocityEffectsAndNextTickUntilTeleportCompletion() throws Exception {
    String source = Files.readString(SOURCE);
    int teleport = source.indexOf("PaperCompat.teleportAsync(trident, freed)");
    int completion = source.indexOf("private void completeReleaseTeleport");
    int outcome = source.indexOf("successfulReleaseTeleport(success, failure)", completion);
    int resumed = source.indexOf("J.runEntity(trident, () -> recallTick", outcome);
    int continuation = source.indexOf("private void continueRecallOwned", resumed);
    int velocity = source.indexOf("trident.setVelocity", continuation);
    int effect = source.indexOf("fx(tridentLocation, FxPriority.TRAIL)", velocity);
    int nextTick = source.indexOf("scheduleNextRecall(trident, p, level, ticksLived)", effect);

    assertThat(teleport).isGreaterThanOrEqualTo(0);
    assertThat(completion).isGreaterThan(teleport);
    assertThat(outcome).isGreaterThan(completion);
    assertThat(resumed).isGreaterThan(outcome);
    assertThat(continuation).isGreaterThan(resumed);
    assertThat(velocity).isGreaterThan(continuation);
    assertThat(effect).isGreaterThan(velocity);
    assertThat(nextTick).isGreaterThan(effect);
    assertThat(source).doesNotContain("trident.teleport(freed)");
  }

  @Test
  void projectileDamageUsesLaunchSnapshotAndRewardsOnShooterOwner() throws Exception {
    String source = Files.readString(SOURCE);
    int launch = source.indexOf("public void on(ProjectileLaunchEvent e)");
    int storedLevel = source.indexOf("set(masteryLevelKey, PersistentDataType.INTEGER, level)", launch);
    int recallGate = source.indexOf("if (!getConfig().enableRecall)", launch);
    int damage = source.indexOf("public void on(EntityDamageByEntityEvent e)");
    int storedRead = source.indexOf("get(masteryLevelKey, PersistentDataType.INTEGER)", damage);
    int ownerReward = source.indexOf(
        "J.runEntity(context.attacker(), () -> rewardHitOwned(context.attacker()))", damage);

    assertThat(storedLevel).isGreaterThan(launch);
    assertThat(recallGate).isGreaterThan(storedLevel);
    assertThat(storedRead).isGreaterThan(damage);
    assertThat(ownerReward).isGreaterThan(damage);
  }

  @Test
  void recallSnapshotsPlayerStateBeforeReturningToTridentOwner() throws Exception {
    String source = Files.readString(SOURCE);
    int recall = source.indexOf("private void recallTick");
    int playerHop = source.indexOf("J.runEntity(p, () -> captureRecallTargetOwned", recall);
    int capture = source.indexOf("private void captureRecallTargetOwned", playerHop);
    int playerLocation = source.indexOf("p.getLocation()", capture);
    int tridentHop = source.indexOf("J.runEntity(trident", playerLocation);
    int apply = source.indexOf("private void applyRecallTargetOwned", tridentHop);
    String recallBody = source.substring(recall, capture);
    String applyBody = source.substring(apply, source.indexOf("private void beginReleaseTeleport", apply));

    assertThat(playerHop).isGreaterThan(recall);
    assertThat(playerLocation).isGreaterThan(capture);
    assertThat(tridentHop).isGreaterThan(playerLocation);
    assertThat(apply).isGreaterThan(tridentHop);
    assertThat(recallBody).doesNotContain("p.isOnline()", "p.getLocation()");
    assertThat(applyBody).doesNotContain("p.isOnline()", "p.getLocation()");
  }
}
