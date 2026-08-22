package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectRedstonePulseTest {
  private static final UUID WORLD_ID = UUID.fromString("53abcf67-3c44-4590-8331-2ecdb2193ea2");

  @Test
  void theEmitterIsTheBoundBlockItselfAndNotAnAdjacentFace() {
    ArchitectRedstonePulse.Emitter emitter = ArchitectRedstonePulse.emitter(WORLD_ID, 10, 64, 20);

    assertThat(emitter).isEqualTo(new ArchitectRedstonePulse.Emitter(WORLD_ID, 10, 64, 20));
    assertThat(emitter.isInChunk(WORLD_ID, 0, 1)).isTrue();
    assertThat(emitter.isInChunk(UUID.randomUUID(), 0, 1)).isFalse();
    assertThat(ArchitectRedstonePulse.emitter(null, 10, 64, 20)).isNull();
  }

  @Test
  void onlyCartesianFacesCanBeBound() {
    assertThat(ArchitectRedstonePulse.isBindableFace(BlockFace.NORTH)).isTrue();
    assertThat(ArchitectRedstonePulse.isBindableFace(BlockFace.UP)).isTrue();
    assertThat(ArchitectRedstonePulse.isBindableFace(BlockFace.DOWN)).isTrue();
    assertThat(ArchitectRedstonePulse.isBindableFace(BlockFace.SELF)).isFalse();
    assertThat(ArchitectRedstonePulse.isBindableFace(BlockFace.NORTH_EAST)).isFalse();
    assertThat(ArchitectRedstonePulse.isBindableFace(null)).isFalse();
  }

  @Test
  void overlappingPulseKeepsTheFirstCapturedStateAndTheNewestGeneration() {
    ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Emitter emitter = new ArchitectRedstonePulse.Emitter(WORLD_ID, 11, 64, 20);
    List<ArchitectRedstonePulse.Snapshot> unpowered = List.of(snapshot(11, 64, 20));
    List<ArchitectRedstonePulse.Snapshot> stale = List.of(snapshot(11, 65, 20));

    ArchitectRedstonePulse.Activation first = pulses.begin(emitter, unpowered);
    ArchitectRedstonePulse.Activation second = pulses.begin(emitter, stale);

    assertThat(first.firstPulse()).isTrue();
    assertThat(second.firstPulse()).isFalse();
    assertThat(second.snapshots()).isEqualTo(unpowered);
    assertThat(pulses.complete(first)).isFalse();
    assertThat(pulses.owns(emitter)).isTrue();
    assertThat(pulses.complete(second)).isTrue();
    assertThat(pulses.owns(emitter)).isFalse();
  }

  @Test
  void pulsesWithoutAnyDriveableBlockAreRejected() {
    ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Emitter emitter = new ArchitectRedstonePulse.Emitter(WORLD_ID, 12, 64, 20);

    assertThat(pulses.begin(emitter, List.of())).isNull();
    assertThat(pulses.begin(emitter, null)).isNull();
    assertThat(pulses.begin(null, List.of(snapshot(12, 64, 20)))).isNull();
    assertThat(pulses.owns(emitter)).isFalse();
  }

  @Test
  void cancellingReturnsTheStateToRestore() {
    ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Emitter emitter = new ArchitectRedstonePulse.Emitter(WORLD_ID, 13, 64, 20);
    List<ArchitectRedstonePulse.Snapshot> snapshots = List.of(snapshot(13, 64, 20));
    pulses.begin(emitter, snapshots);

    ArchitectRedstonePulse.Restoration restoration = pulses.cancel(emitter);

    assertThat(restoration).isEqualTo(new ArchitectRedstonePulse.Restoration(emitter, snapshots));
    assertThat(pulses.owns(emitter)).isFalse();
    assertThat(pulses.cancel(emitter)).isNull();
  }

  @Test
  void closingReturnsOwnedEmittersAndRejectsLaterPulses() {
    ArchitectRedstonePulse pulses = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Emitter emitter = new ArchitectRedstonePulse.Emitter(WORLD_ID, 14, 64, 20);
    List<ArchitectRedstonePulse.Snapshot> snapshots = List.of(snapshot(14, 64, 20));
    pulses.begin(emitter, snapshots);

    Set<ArchitectRedstonePulse.Restoration> restorations = pulses.close();

    assertThat(restorations).containsExactly(
        new ArchitectRedstonePulse.Restoration(emitter, snapshots));
    assertThat(pulses.owns(emitter)).isFalse();
    assertThat(pulses.begin(emitter, snapshots)).isNull();
  }

  @Test
  void newerRuntimeLeasePreventsOlderRuntimeCleanup() {
    ArchitectRedstonePulse oldRuntime = new ArchitectRedstonePulse();
    ArchitectRedstonePulse newRuntime = new ArchitectRedstonePulse();
    ArchitectRedstonePulse.Emitter emitter = new ArchitectRedstonePulse.Emitter(WORLD_ID, 15, 64, 20);
    List<ArchitectRedstonePulse.Snapshot> original = List.of(snapshot(15, 64, 20));

    ArchitectRedstonePulse.Activation oldActivation = oldRuntime.begin(emitter, original);
    ArchitectRedstonePulse.Activation newActivation = newRuntime.begin(emitter,
        List.of(snapshot(15, 65, 20)));

    assertThat(oldRuntime.complete(oldActivation)).isFalse();
    assertThat(oldRuntime.close()).isEmpty();
    assertThat(newRuntime.complete(newActivation)).isTrue();
    assertThat(newActivation.snapshots()).isEqualTo(original);
  }

  private static ArchitectRedstonePulse.Snapshot snapshot(int x, int y, int z) {
    return new ArchitectRedstonePulse.Snapshot(x, y, z, null, null);
  }
}
