package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectRedstonePulseTest {
  private static final UUID WORLD_ID = UUID.fromString("53abcf67-3c44-4590-8331-2ecdb2193ea2");

  @Test
  void receiverUsesTheExposedFaceForEveryCartesianDirection() {
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, BlockFace.NORTH))
        .isEqualTo(new ArchitectRedstonePulse.Receiver(WORLD_ID, 10, 64, 19));
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, BlockFace.EAST))
        .isEqualTo(new ArchitectRedstonePulse.Receiver(WORLD_ID, 11, 64, 20));
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, BlockFace.SOUTH))
        .isEqualTo(new ArchitectRedstonePulse.Receiver(WORLD_ID, 10, 64, 21));
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, BlockFace.WEST))
        .isEqualTo(new ArchitectRedstonePulse.Receiver(WORLD_ID, 9, 64, 20));
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, BlockFace.UP))
        .isEqualTo(new ArchitectRedstonePulse.Receiver(WORLD_ID, 10, 65, 20));
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, BlockFace.DOWN))
        .isEqualTo(new ArchitectRedstonePulse.Receiver(WORLD_ID, 10, 63, 20));
  }

  @Test
  void receiverRejectsNonCartesianAndMissingFaces() {
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, BlockFace.SELF)).isNull();
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, BlockFace.NORTH_EAST)).isNull();
    assertThat(ArchitectRedstonePulse.receiver(WORLD_ID, 10, 64, 20, null)).isNull();
  }

  @Test
  void overlappingPulseKeepsTheNewestGenerationActive() {
    ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Receiver receiver = new ArchitectRedstonePulse.Receiver(WORLD_ID, 11, 64, 20);

    ArchitectRedstonePulse.Activation first = pulses.begin(receiver, Material.CAVE_AIR);
    ArchitectRedstonePulse.Activation second = pulses.begin(receiver, Material.AIR);

    assertThat(first.firstPulse()).isTrue();
    assertThat(second.firstPulse()).isFalse();
    assertThat(pulses.complete(first)).isFalse();
    assertThat(pulses.owns(receiver)).isTrue();
    assertThat(pulses.complete(second)).isTrue();
    assertThat(pulses.owns(receiver)).isFalse();
  }

  @Test
  void closingReturnsOwnedReceiversAndRejectsLaterPulses() {
    ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Receiver receiver = new ArchitectRedstonePulse.Receiver(WORLD_ID, 11, 64, 20);
    pulses.begin(receiver, Material.VOID_AIR);

    Set<ArchitectRedstonePulse.Restoration> restorations = pulses.close();

    assertThat(restorations).containsExactly(
        new ArchitectRedstonePulse.Restoration(receiver, Material.VOID_AIR));
    assertThat(pulses.owns(receiver)).isFalse();
    assertThat(pulses.begin(receiver, Material.AIR)).isNull();
  }

  @Test
  void journalCoordinatesAndAirKindsRoundTripAcrossNegativeWorldHeight() {
    for (Material material : Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR)) {
      int encoded = ArchitectRedstonePulse.encodeBlock(15, 319, 0, -64, material);

      assertThat(ArchitectRedstonePulse.decodeX(encoded)).isEqualTo(15);
      assertThat(ArchitectRedstonePulse.decodeZ(encoded)).isZero();
      assertThat(ArchitectRedstonePulse.decodeY(encoded, -64)).isEqualTo(319);
      assertThat(ArchitectRedstonePulse.decodeOriginalMaterial(encoded)).isEqualTo(material);
    }
  }

  @Test
  void pulseRejectsAReceiverWithoutAnOriginalAirMaterial() {
    ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Receiver receiver = new ArchitectRedstonePulse.Receiver(WORLD_ID, 11, 64, 20);

    assertThat(pulses.begin(receiver, Material.STONE)).isNull();
    assertThat(pulses.begin(receiver, null)).isNull();
    assertThat(pulses.owns(receiver)).isFalse();
  }

  @Test
  void newerRuntimeLeasePreventsOlderRuntimeCleanup() {
    ArchitectRedstonePulse oldRuntime = new ArchitectRedstonePulse();
    ArchitectRedstonePulse newRuntime = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Receiver receiver = new ArchitectRedstonePulse.Receiver(WORLD_ID, 11, 64, 20);

    ArchitectRedstonePulse.Activation oldActivation = oldRuntime.begin(receiver, Material.CAVE_AIR);
    ArchitectRedstonePulse.Activation newActivation = newRuntime.begin(receiver, Material.REDSTONE_BLOCK);

    assertThat(oldRuntime.complete(oldActivation)).isFalse();
    assertThat(oldRuntime.close()).isEmpty();
    assertThat(newRuntime.complete(newActivation)).isTrue();
    assertThat(newActivation.originalMaterial()).isEqualTo(Material.CAVE_AIR);
  }
}
