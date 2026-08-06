package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate channel must commit its full cost (eye reservation and cooldown) when the channel
 * STARTS, not when it ends. Committing at the end let players start the channel for free
 * levitation, stow or drop the eye mid-channel, and abort with no cost and no cooldown —
 * an infinite float loop.
 */
class RiftGateChannelCommitmentTest {
  private static String openEye;
  private static String isAuthorizedAfterChannel;
  private static String finishGateTeleportOwned;

  @BeforeAll
  static void readSource() throws IOException {
    String source = Files.readString(
        Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftGate.java"));
    openEye = methodRegion(source, "private void openEye", "private void authorizeAndTeleport");
    isAuthorizedAfterChannel = methodRegion(source,
        "private boolean isAuthorizedAfterChannel", "private GateReservation reserveGateEye");
    finishGateTeleportOwned = methodRegion(source,
        "private void finishGateTeleportOwned", "private boolean settleGateReservation");
  }

  private static String methodRegion(String source, String from, String to) {
    int start = source.indexOf(from);
    int end = source.indexOf(to);
    assertThat(start).as("region start: " + from).isNotNegative();
    assertThat(end).as("region end: " + to).isGreaterThan(start);
    return source.substring(start, end);
  }

  @Test
  void theEyeIsReservedBeforeTheChannelEffectsAreGranted() {
    int reserve = openEye.indexOf("reserveGateEye");
    int effects = openEye.indexOf("addPotionEffect");
    assertThat(reserve).as("openEye reserves the gate eye itself").isNotNegative();
    assertThat(effects).as("openEye grants the channel effects").isNotNegative();
    assertThat(reserve).as("reservation happens before levitation is granted").isLessThan(effects);
  }

  @Test
  void theCooldownBurnsWhenTheChannelStartsAndOnlyThen() {
    assertThat(openEye).as("cooldown is marked at channel start").contains("gateCooldown.mark");
    assertThat(finishGateTeleportOwned)
        .as("the successful-teleport path must not mark the cooldown a second time")
        .doesNotContain("gateCooldown.mark");
  }

  @Test
  void postChannelAuthorizationCannotBeDodgedThroughTheInventory() {
    assertThat(isAuthorizedAfterChannel)
        .as("the end-of-channel check must not depend on what the hand holds; the eye is already reserved")
        .doesNotContain("getItemInMainHand");
    assertThat(isAuthorizedAfterChannel)
        .as("the end-of-channel check must not consult the cooldown the channel itself just marked")
        .doesNotContain("gateCooldown.isReady");
  }
}
