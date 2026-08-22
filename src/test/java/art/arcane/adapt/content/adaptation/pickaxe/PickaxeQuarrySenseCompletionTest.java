package art.arcane.adapt.content.adaptation.pickaxe;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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

  @Test
  void completionUsesTheRevalidatedLevelForEverySettledEffect() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/pickaxe/PickaxeQuarrySense.java"));

    assertThat(source).contains(
        "int activeLevel = getActiveLevel(p);",
        "canCompleteScan(activeLevel, result.world(), p.getWorld())",
        "getCooldownTicks(activeLevel)",
        "getDurabilityCost(currentHand, activeLevel)",
        "getHighlightTicks(activeLevel)"
    );
  }
}
