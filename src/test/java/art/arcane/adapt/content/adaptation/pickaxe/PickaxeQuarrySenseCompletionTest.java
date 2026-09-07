package art.arcane.adapt.content.adaptation.pickaxe;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PickaxeQuarrySenseCompletionTest {
  @Test
  void completionRequiresAnActiveAdaptationInTheScannedWorld() {
    World scannedWorld = mock(World.class);
    World otherWorld = mock(World.class);

    assertThat(PickaxeQuarrySense.canCompleteScan(1, scannedWorld, scannedWorld)).isTrue();
    assertThat(PickaxeQuarrySense.canCompleteScan(0, scannedWorld, scannedWorld)).isFalse();
    assertThat(PickaxeQuarrySense.canCompleteScan(1, scannedWorld, otherWorld)).isFalse();
    assertThat(PickaxeQuarrySense.canCompleteScan(1, null, scannedWorld)).isFalse();
  }
}
