package art.arcane.adapt.content.adaptation.discovery;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryInsightRuntimeTest {
  @Test
  void productionWorkLimitsRemainHardAtOneThousandContenders() {
    assertThat(InsightWorkLimits.viewerUpdates(1_000)).isEqualTo(32);
    assertThat(InsightWorkLimits.priorityViewerUpdates(32)).isEqualTo(8);
    assertThat(InsightWorkLimits.damageNumbers(1_000)).isEqualTo(16);
  }

  @Test
  void damageNumberBudgetIsExactAndResetsAtNextTick() {
    AtomicLong clock = new AtomicLong();
    InsightWorkBudget budget = new InsightWorkBudget(50L, clock::get);
    int damageGrants = 0;

    for (int index = 0; index < 1_000; index++) {
      if (budget.tryAcquireDamageNumber(16)) {
        damageGrants++;
      }
    }

    assertThat(damageGrants).isEqualTo(16);
    assertThat(budget.damageNumbers()).isEqualTo(16);

    clock.set(50L);
    assertThat(budget.tryAcquireDamageNumber(16)).isTrue();
    assertThat(budget.damageNumbers()).isEqualTo(1);
  }

  @Test
  void requestGateRejectsStaleCrossOwnerCallbacks() {
    InsightRequestGate gate = new InsightRequestGate();
    UUID playerId = new UUID(0L, 1L);
    long first = gate.advance(playerId);
    long second = gate.advance(playerId);

    assertThat(gate.isCurrent(playerId, first)).isFalse();
    assertThat(gate.isCurrent(playerId, second)).isTrue();

    gate.clear();
    assertThat(gate.isCurrent(playerId, second)).isFalse();
  }

  @Test
  void tameableHudShowsLiveAttributesAndStableHandState() {
    DiscoveryInsight.AnimalStats stats = new DiscoveryInsight.AnimalStats(0.32D, 0.71D, 5.5D, true);
    DiscoveryInsight.HudVitals vitals = new DiscoveryInsight.HudVitals("Swift", 24D, 30D, stats);

    String text = DiscoveryInsight.buildHudText(vitals, 12);

    assertThat(text).contains("Swift", "24", "30", "Speed", "0.32", "Jump", "0.71", "Attack", "5.5",
        "Stable Hand enhanced");
  }

  @Test
  void ordinaryCreatureHudDoesNotInventAnimalAttributes() {
    DiscoveryInsight.HudVitals vitals = new DiscoveryInsight.HudVitals("Zombie", 12D, 20D, null);

    String text = DiscoveryInsight.buildHudText(vitals, 12);

    assertThat(text).contains("Zombie", "12", "20");
    assertThat(text).doesNotContain("Speed", "Jump", "Attack", "Stable Hand");
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
