package art.arcane.adapt.content.adaptation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledOwnershipAuditTest {
  private static final Path FISH_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/seaborrne/SeaborneFishWhisperer.java");
  private static final Path CORAL_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/seaborrne/SeaborneCoralGardener.java");
  private static final Path DECOY_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/stealth/StealthShadowDecoy.java");

  @Test
  void dolphinChargeUsesCapturedVictimLocation() throws Exception {
    String source = Files.readString(FISH_SOURCE);
    int snapshot = source.indexOf("Location victimLocation = victim.getLocation().clone()");
    int dispatch = source.indexOf("chargeDolphin(dolphin, victimLocation)", snapshot);
    int charge = source.indexOf("private void chargeDolphin(Dolphin dolphin, Location victimLocation)", dispatch);
    String chargeBody = source.substring(charge, source.indexOf("private double getSchoolRange", charge));

    assertThat(snapshot).isGreaterThanOrEqualTo(0);
    assertThat(dispatch).isGreaterThan(snapshot);
    assertThat(charge).isGreaterThan(dispatch);
    assertThat(chargeBody).doesNotContain("victim.isValid()", "victim.isDead()", "victim.getLocation()");
  }

  @Test
  void coralRegionMutationDoesNotRecheckPolicyThroughRemotePlayer() throws Exception {
    String source = Files.readString(CORAL_SOURCE);
    int authorization = source.indexOf("canBlockPlace(p, target.getLocation())");
    int dispatch = source.indexOf("J.runAt(growTarget.getLocation()", authorization);
    int growth = source.indexOf("private void growCoral", dispatch);
    String growthBody = source.substring(growth, source.indexOf("private boolean consumeBoneMeal", growth));

    assertThat(authorization).isGreaterThanOrEqualTo(0);
    assertThat(dispatch).isGreaterThan(authorization);
    assertThat(growth).isGreaterThan(dispatch);
    assertThat(growthBody).doesNotContain("canBlockPlace(p");
  }

  @Test
  void decoyAggroUsesOwnerSnapshotInsideMobCallbacks() throws Exception {
    String source = Files.readString(DECOY_SOURCE);
    int snapshot = source.indexOf("Location ownerEyeLocation = owner.getEyeLocation().clone()");
    int anchorHop = source.indexOf("refreshAnchor(state, now, ownerEyeLocation)", snapshot);
    int redirect = source.indexOf("private void redirectAggro(DecoySession state, Location ownerEyeLocation)");
    int refresh = source.indexOf("private void refreshOwner", redirect);
    String redirectBody = source.substring(redirect, refresh);

    assertThat(snapshot).isGreaterThanOrEqualTo(0);
    assertThat(anchorHop).isGreaterThan(snapshot);
    assertThat(redirect).isGreaterThanOrEqualTo(0);
    assertThat(redirectBody)
        .contains("mob.hasLineOfSight(ownerEyeLocation)", "isProtectedFriendlyToOwner(ownerId, mob)")
        .doesNotContain("mob.hasLineOfSight(owner)", "isProtectedFriendly(owner, mob)");
  }
}
