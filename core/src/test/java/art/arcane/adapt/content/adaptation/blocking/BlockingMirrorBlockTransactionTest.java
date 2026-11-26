package art.arcane.adapt.content.adaptation.blocking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingMirrorBlockTransactionTest {
  @Test
  void onlyAConfirmedTeleportCanConfigureTheReflection() {
    assertThat(BlockingMirror.teleportCompleted(true, null)).isTrue();
    assertThat(BlockingMirror.teleportCompleted(false, null)).isFalse();
    assertThat(BlockingMirror.teleportCompleted(null, null)).isFalse();
    assertThat(BlockingMirror.teleportCompleted(
        true,
        new IllegalStateException("teleport failed")
    )).isFalse();
  }
}
