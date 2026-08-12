package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RiftConduitGestureTest {
  private static final Path CONDUIT_SOURCE =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftConduit.java");

  @Test
  void sneakClickingAContainerWithAPlainPearlCaptures() {
    assertThat(RiftConduit.resolveGesture(false, true, true, true, true))
        .isEqualTo(RiftConduit.ConduitGesture.CAPTURE);
  }

  @Test
  void captureNeedsAllOfSneakContainerAndAPlainPearl() {
    assertThat(RiftConduit.resolveGesture(false, true, false, true, true))
        .isEqualTo(RiftConduit.ConduitGesture.IGNORE);
    assertThat(RiftConduit.resolveGesture(false, true, true, false, true))
        .isEqualTo(RiftConduit.ConduitGesture.IGNORE);
    assertThat(RiftConduit.resolveGesture(false, true, true, true, false))
        .isEqualTo(RiftConduit.ConduitGesture.IGNORE);
  }

  @Test
  void clickingASecondContainerWithATaglockBindsWithoutNeedingSneak() {
    assertThat(RiftConduit.resolveGesture(true, true, false, true, false))
        .isEqualTo(RiftConduit.ConduitGesture.BIND);
    assertThat(RiftConduit.resolveGesture(true, true, true, true, false))
        .isEqualTo(RiftConduit.ConduitGesture.BIND);
  }

  @Test
  void aTaglockAimedAtAnythingElseExplainsItselfInsteadOfThrowing() {
    assertThat(RiftConduit.resolveGesture(true, true, false, false, false))
        .isEqualTo(RiftConduit.ConduitGesture.NEED_CONTAINER);
  }

  @Test
  void taglocksStayInertForPlayersWithoutTheAdaptation() {
    assertThat(RiftConduit.resolveGesture(true, false, false, true, false))
        .isEqualTo(RiftConduit.ConduitGesture.CANCEL_ONLY);
    assertThat(RiftConduit.resolveGesture(true, false, true, false, false))
        .isEqualTo(RiftConduit.ConduitGesture.CANCEL_ONLY);
  }

  @Test
  void plainPearlsStayVanillaForPlayersWithoutTheAdaptation() {
    assertThat(RiftConduit.resolveGesture(false, false, true, true, true))
        .isEqualTo(RiftConduit.ConduitGesture.IGNORE);
  }

  @Test
  void gesturesHonorNativeBlockUseDenialAndContainerProtection() throws IOException {
    String source = Files.readString(CONDUIT_SOURCE);

    assertThat(source)
        .contains("canInteract(p, clicked.getLocation())")
        .contains("e.useInteractedBlock() == Event.Result.DENY")
        .contains("canAccessContainer(p, clicked)");
  }

  @Test
  void bindHoldsTheFollowUpWindowOpenAcrossTheHeldItemSwap() throws IOException {
    String source = Files.readString(CONDUIT_SOURCE);
    int markIndex = source.indexOf("markCaptureFollowUp(p);\n        completeBind(");

    assertThat(markIndex).isGreaterThan(0);
  }

  @Test
  void doubleChestFlowResolvesBothHalvesInsteadOfTheFlooredMidpoint() throws IOException {
    String source = Files.readString(CONDUIT_SOURCE);

    assertThat(source)
        .contains(
            "holder instanceof DoubleChest doubleChest",
            "addContainerLocation(sides, doubleChest.getLeftSide())",
            "addContainerLocation(sides, doubleChest.getRightSide())")
        .doesNotContain("Location loc = inv.getLocation();");
  }
}
