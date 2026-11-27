package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiftConduitGestureTest {
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
}
