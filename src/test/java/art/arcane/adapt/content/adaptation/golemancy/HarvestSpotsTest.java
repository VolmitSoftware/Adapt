package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HarvestSpotsTest {
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000022f3");
  private static final NodeRecord AREA = area(2, 1, 2);

  private final HarvestSpots spots = new HarvestSpots();

  @Test
  void onlyTendAndPlantSpotsOfAScannedNodeAreKept() {
    spots.record(KEY, 1, 64, 1, HarvesterRules.Spot.TEND);
    assertThat(spots.of(KEY)).isEmpty();

    spots.scanned(KEY, AREA, 0L);
    spots.record(KEY, 1, 64, 1, HarvesterRules.Spot.TEND);
    spots.record(KEY, 2, 63, 1, HarvesterRules.Spot.PLANT);
    spots.record(KEY, 3, 64, 1, HarvesterRules.Spot.RIPE);
    spots.record(KEY, 4, 63, 1, HarvesterRules.Spot.TILL);

    assertThat(spots.of(KEY)).containsExactlyInAnyOrder(new HarvestSpots.Mark(HarvesterRules.Spot.TEND, 1, 64, 1),
        new HarvestSpots.Mark(HarvesterRules.Spot.PLANT, 2, 63, 1));

    spots.record(KEY, 1, 64, 1, HarvesterRules.Spot.RIPE);
    assertThat(spots.of(KEY)).containsExactly(new HarvestSpots.Mark(HarvesterRules.Spot.PLANT, 2, 63, 1));
  }

  @Test
  void eachNodeKeepsABoundedNumberOfSpots() {
    spots.scanned(KEY, AREA, 0L);
    for (int x = 0; x < HarvestSpots.MAX_MARKS_PER_NODE + 10; x++) {
      spots.record(KEY, x, 64, 0, HarvesterRules.Spot.TEND);
    }

    assertThat(spots.of(KEY)).hasSize(HarvestSpots.MAX_MARKS_PER_NODE);
    spots.record(KEY, 0, 64, 0, HarvesterRules.Spot.PLANT);
    assertThat(spots.of(KEY)).contains(new HarvestSpots.Mark(HarvesterRules.Spot.PLANT, 0, 64, 0));
  }

  @Test
  void nodesThatStopScanningAreSweptAfterAMinute() {
    spots.scanned(KEY, AREA, 1_000L);
    spots.record(KEY, 1, 64, 1, HarvesterRules.Spot.TEND);

    spots.sweep(1_000L + HarvestSpots.STALE_MILLIS - 1L);
    assertThat(spots.pending()).isTrue();

    spots.sweep(1_000L + HarvestSpots.STALE_MILLIS);
    assertThat(spots.pending()).isFalse();
    assertThat(spots.of(KEY)).isEmpty();
  }

  @Test
  void changingTheNodesRadiiClearsItsMarks() {
    spots.scanned(KEY, AREA, 0L);
    spots.record(KEY, 1, 64, 1, HarvesterRules.Spot.TEND);

    spots.scanned(KEY, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(2, 1, 2).priority(5).build(), 100L);
    assertThat(spots.of(KEY)).hasSize(1);

    spots.scanned(KEY, area(1, 1, 2), 200L);
    assertThat(spots.of(KEY)).isEmpty();
  }

  @Test
  void droppingAMarkForgetsOnlyThatSpot() {
    spots.scanned(KEY, AREA, 0L);
    spots.record(KEY, 1, 64, 1, HarvesterRules.Spot.TEND);
    spots.record(KEY, 2, 63, 1, HarvesterRules.Spot.PLANT);

    spots.drop(KEY, 1, 64, 1);

    assertThat(spots.of(KEY)).containsExactly(new HarvestSpots.Mark(HarvesterRules.Spot.PLANT, 2, 63, 1));
  }

  private static NodeRecord area(int x, int y, int z) {
    return NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).radii(x, y, z).build();
  }
}
