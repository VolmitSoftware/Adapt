package art.arcane.adapt.api.claims;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimSnapshotTest {
  private static final UUID WORLD = new UUID(0, 1);
  private static final UUID CITY = new UUID(0, 2);
  private static final UUID FOUNDER = new UUID(0, 3);
  private static final UUID MEMBER = new UUID(0, 4);
  private static final ClaimBlock ANCHOR = new ClaimBlock(WORLD, 0, 64, 0);
  private static final ClaimRanks RANKS = new ClaimRanks(true, 1, 1, 4, true);

  @ParameterizedTest
  @CsvSource({"-2, 0", "2, 0", "0, -2", "0, 2", "-2, -2", "-2, 2", "2, -2", "2, 2"})
  void personalPlotsCannotOverlapMainFootprintAtEdgesOrCorners(int chunkX, int chunkZ) {
    assertThatThrownBy(() -> snapshot(chunkX, chunkZ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("personal plot ownership");
  }

  @ParameterizedTest
  @CsvSource({"-3, 0", "3, 0", "0, -3", "0, 3", "-3, -2", "-3, 2", "3, -2", "3, 2",
      "-2, -3", "-2, 3", "2, -3", "2, 3"})
  void personalPlotsAllowThreeChunkSeparationAlongEitherAxis(int chunkX, int chunkZ) {
    ClaimSnapshot snapshot = snapshot(chunkX, chunkZ);
    assertThat(snapshot.plots()).containsOnlyKeys(MEMBER);
    assertThat(snapshot.plotAt(new ClaimChunk(WORLD, chunkX, chunkZ))).isPresent();
    assertThat(snapshot.plotAt(ANCHOR.chunk())).isEmpty();
  }

  private ClaimSnapshot snapshot(int chunkX, int chunkZ) {
    ClaimBlock plotAnchor = new ClaimBlock(WORLD, chunkX * 16, 64, chunkZ * 16);
    Set<ClaimChunk> plotChunks = ClaimSnapshot.footprint(plotAnchor.chunk());
    Set<ClaimChunk> cityChunks = new HashSet<>(ClaimSnapshot.footprint(ANCHOR.chunk()));
    cityChunks.addAll(plotChunks);
    ClaimCity city = new ClaimCity(CITY, "Town", FOUNDER, ANCHOR, Set.of(MEMBER), cityChunks, RANKS, 0);
    ClaimPlot plot = new ClaimPlot(MEMBER, CITY, plotAnchor, plotChunks);
    return new ClaimSnapshot(Map.of(CITY, city), Map.of(MEMBER, plot));
  }
}
