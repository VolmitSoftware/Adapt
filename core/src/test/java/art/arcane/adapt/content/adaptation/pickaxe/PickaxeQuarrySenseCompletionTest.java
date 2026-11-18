package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxeQuarrySenseCompletionTest {
  @Test
  void completionRequiresAnActiveAdaptationInTheScannedWorld() {
    FakeWorld scannedWorld = new FakeWorld("world");
    FakeWorld otherWorld = new FakeWorld("world_nether");

    assertThat(PickaxeQuarrySense.canCompleteScan(1, scannedWorld, scannedWorld)).isTrue();
    assertThat(PickaxeQuarrySense.canCompleteScan(0, scannedWorld, scannedWorld)).isFalse();
    assertThat(PickaxeQuarrySense.canCompleteScan(1, scannedWorld, otherWorld)).isFalse();
    assertThat(PickaxeQuarrySense.canCompleteScan(1, null, scannedWorld)).isFalse();
  }
}
