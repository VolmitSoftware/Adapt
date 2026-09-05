package art.arcane.adapt.content.adaptation.discovery;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryInsightRuntimeTest {
  @Test
  void productionWorkLimitsRemainHardAtOneThousandContenders() {
    assertThat(InsightWorkLimits.viewerUpdates(1_000)).isEqualTo(32);
    assertThat(InsightWorkLimits.priorityViewerUpdates(32)).isEqualTo(8);
  }

  @Test
  void requestGateRejectsStaleCrossOwnerCallbacks() {
    InsightRequestGate gate = new InsightRequestGate();
    UUID playerId = new UUID(0L, 1L);
    long first = gate.advance(playerId);
    long second = gate.advance(playerId);

    assertThat(gate.isCurrent(playerId, first)).isFalse();
    assertThat(gate.isCurrent(playerId, second)).isTrue();

    gate.remove(playerId);
    assertThat(gate.isCurrent(playerId, second)).isFalse();
    long rejoined = gate.advance(playerId);
    assertThat(gate.isCurrent(playerId, second)).isFalse();
    assertThat(gate.isCurrent(playerId, rejoined)).isTrue();

    gate.clear();
    assertThat(gate.isCurrent(playerId, rejoined)).isFalse();
  }

  @Test
  void insightSuppliesLiveDetailsAndStableHandState() {
    DiscoveryInsight.InsightStats stats = new DiscoveryInsight.InsightStats(
        "Horse", 0.32D, 0.71D, 4D, 0.25D, 16D, true);

    String text = String.join("\n", DiscoveryInsight.buildInsightDetails(stats));

    assertThat(text).contains("Horse", "Speed", "0.32", "Jump", "0.71", "Toughness", "4",
        "Knockback Resistance", "0.25", "Detection Range", "16", "Stable Hand enhanced");
  }

  @Test
  void missingCreatureAttributesDoNotProduceInventedStats() {
    DiscoveryInsight.InsightStats stats = new DiscoveryInsight.InsightStats(
        "Zombie", 0.23D, Double.NaN, Double.NaN, Double.POSITIVE_INFINITY, 35D, false);

    List<String> details = DiscoveryInsight.buildInsightDetails(stats);
    String text = String.join("\n", details);

    assertThat(details).hasSize(3);
    assertThat(text).contains("Zombie", "Speed", "0.23", "Detection Range", "35");
    assertThat(text).doesNotContain("Jump", "Toughness", "Knockback Resistance", "Stable Hand", "NaN", "Infinity");
  }

  @Test
  void insightLeavesNearbyGlossOverlaysEnabledByDefault() {
    assertThat(new DiscoveryInsight.Config().restrictGlossToInsight).isFalse();
  }

  @Test
  void onlyBlocksStrictlyCloserThanTheEntityOccludeInsight() {
    Vector origin = new Vector(0D, 0D, 0D);
    Vector entityHit = new Vector(0D, 0D, 10D);

    assertThat(DiscoveryInsight.isOccluded(origin, entityHit, new Vector(0D, 0D, 5D))).isTrue();
    assertThat(DiscoveryInsight.isOccluded(origin, entityHit, new Vector(0D, 0D, 10D))).isFalse();
    assertThat(DiscoveryInsight.isOccluded(origin, entityHit, new Vector(0D, 0D, 12D))).isFalse();
    assertThat(DiscoveryInsight.isOccluded(origin, entityHit, null)).isFalse();
  }
}
