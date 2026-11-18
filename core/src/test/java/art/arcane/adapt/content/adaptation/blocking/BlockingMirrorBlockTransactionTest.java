package art.arcane.adapt.content.adaptation.blocking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingMirrorBlockTransactionTest {
  @Test
  void onlyAConfirmedTeleportCanConfigureTheReflection() {
    assertThat(BlockingMirrorBlock.teleportCompleted(true, null)).isTrue();
    assertThat(BlockingMirrorBlock.teleportCompleted(false, null)).isFalse();
    assertThat(BlockingMirrorBlock.teleportCompleted(null, null)).isFalse();
    assertThat(BlockingMirrorBlock.teleportCompleted(
        true,
        new IllegalStateException("teleport failed")
    )).isFalse();
  }
}
