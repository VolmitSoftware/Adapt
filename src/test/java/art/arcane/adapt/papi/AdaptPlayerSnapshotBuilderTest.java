package art.arcane.adapt.papi;

import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.NewtonCurve;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptPlayerSnapshotBuilderTest {
  private static AdaptPlayerSnapshot build(AdaptPlayerSnapshot previous, PlayerData data, NewtonCurve curve) {
    return AdaptPlayerSnapshotBuilder.build(
        previous,
        data,
        AdaptMutationView.unavailable(),
        curve,
        AdaptPapiFixtures.CONFIGURED_MAX_LEVEL,
        AdaptPapiFixtures.POWER_PER_LEVEL
    );
  }

  @Test
  void shouldSolveTheXpCurveOncePerChangedValueAndNeverAgainWhileNothingChanges() {
    CountingCurve curve = new CountingCurve(AdaptPapiFixtures.SQUARE_CURVE);
    PlayerData data = AdaptPapiFixtures.playerData();

    AdaptPlayerSnapshot first = build(null, data, curve);
    assertEquals(3, curve.solves, "expected one solve for master xp, one for the mining line and one for the zero line");

    curve.reset();
    AdaptPlayerSnapshot second = build(first, data, curve);
    assertEquals(0, curve.solves, "an unchanged player must not re-solve the xp curve");
    assertEquals(0, curve.forwardEvaluations, "an unchanged player must not re-evaluate the xp curve");
    assertSame(first.skills().get("mining"), second.skills().get("mining"));
    assertSame(first.skills(), second.skills());
    assertSame(first.adaptationLevels(), second.adaptationLevels());
    assertSame(first.zeroLine(), second.zeroLine());

    curve.reset();
    data.getSkillLines().get("mining").setXp(36.0D);
    AdaptPlayerSnapshot third = build(second, data, curve);
    assertEquals(1, curve.solves, "only the changed skill line may re-solve the xp curve");
    assertNotSame(second.skills().get("mining"), third.skills().get("mining"));
    assertSame(second.adaptationLevels(), third.adaptationLevels());
    assertEquals("6", third.skills().get("mining").band().levelText());
  }

  @Test
  void publishedMapsRemainImmutableWithoutDefensiveRecopying() {
    AdaptPlayerSnapshot snapshot = build(null, AdaptPapiFixtures.playerData(), AdaptPapiFixtures.SQUARE_CURVE);

    assertThatThrownBy(() -> snapshot.skills().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.adaptationLevels().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldReuseTheLevelBandWhenOnlyTheMultiplierMoves() {
    CountingCurve curve = new CountingCurve(AdaptPapiFixtures.SQUARE_CURVE);
    PlayerData data = AdaptPapiFixtures.playerData();
    AdaptPlayerSnapshot first = build(null, data, curve);

    curve.reset();
    data.getSkillLines().get("mining").setMultiplier(2.5D);
    AdaptPlayerSnapshot second = build(first, data, curve);

    assertEquals(0, curve.solves);
    assertSame(first.skills().get("mining").band(), second.skills().get("mining").band());
    assertEquals("2.50", second.skills().get("mining").multiplierText());
  }

  @Test
  void shouldComputeMaxUsedAndAvailablePowerInOneConsistentPass() {
    PlayerData data = AdaptPapiFixtures.playerData();
    AdaptPlayerSnapshot snapshot = build(null, data, AdaptPapiFixtures.SQUARE_CURVE);

    assertEquals(10, snapshot.maxPower());
    assertEquals(2, snapshot.usedPower());
    assertEquals(snapshot.maxPower() - snapshot.usedPower(), snapshot.availablePower());
  }

  @Test
  void shouldNotLetAPurchaseLandingMidBuildProduceNegativeAvailablePower() {
    PlayerData data = AdaptPapiFixtures.playerData();
    data.setMasterXp(1.0D);
    PlayerSkillLine mining = data.getSkillLines().get("mining");

    for (int index = 0; index < 12; index++) {
      mining.getAdaptations().put("filler-" + index, AdaptPapiFixtures.playerAdaptation("filler-" + index, 1));
    }

    AdaptPlayerSnapshot snapshot = build(null, data, AdaptPapiFixtures.SQUARE_CURVE);
    assertEquals(snapshot.maxPower() - snapshot.usedPower(), snapshot.availablePower());
    assertTrue(snapshot.usedPower() > snapshot.maxPower());
  }

  @Test
  void shouldIndexOnlyLearnedAdaptationsSoTheSnapshotStaysBoundedByWhatTheOwnerHas() {
    PlayerData data = AdaptPapiFixtures.playerData();
    data.getSkillLines().get("mining").getAdaptations().put("mining-unlearned", AdaptPapiFixtures.playerAdaptation("mining-unlearned", 0));

    AdaptPlayerSnapshot snapshot = build(null, data, AdaptPapiFixtures.SQUARE_CURVE);

    assertEquals(2, snapshot.adaptationLevels().size());
    assertEquals(1, snapshot.adaptationLevel("mining-vein"));
    assertEquals(0, snapshot.adaptationLevel("mining-unlearned"));
    assertEquals(0, snapshot.adaptationLevel("nothing-at-all"));
  }

  @Test
  void firstSnapshotSizesTheAdaptationIndexFromCurrentPlayerData() {
    PlayerData data = AdaptPapiFixtures.playerData();
    PlayerSkillLine mining = data.getSkillLines().get("mining");
    for (int index = 0; index < 310; index++) {
      String id = "full-catalog-" + index;
      mining.getAdaptations().put(id, AdaptPapiFixtures.playerAdaptation(id, 1));
    }

    assertEquals(312, AdaptPlayerSnapshotBuilder.expectedAdaptationCount(data.getSkillLines()));
    assertEquals(312, build(null, data, AdaptPapiFixtures.SQUARE_CURVE).adaptationLevels().size());
  }

  @Test
  void shouldExposeAZeroLineForASkillTheOwnerHasNeverTrained() {
    AdaptPlayerSnapshot snapshot = build(null, AdaptPapiFixtures.playerData(), AdaptPapiFixtures.SQUARE_CURVE);

    assertEquals("0", snapshot.zeroLine().band().levelText());
    assertEquals("0.00", snapshot.zeroLine().band().xpText());
    assertEquals(0L, snapshot.zeroLine().knowledge());
  }

  @Test
  void shouldReportMutationsAsUnavailableWhenNoMutationSnapshotHasBeenPublished() {
    AdaptMutationView view = AdaptPlayerSnapshotBuilder.mutationView(null, true, 0L, null);
    assertEquals(false, view.available());
    assertEquals(null, view.source());
    assertSame(view, AdaptMutationView.unavailable());
  }

  private static final class CountingCurve implements NewtonCurve {
    private final NewtonCurve delegate;
    private int solves;
    private int forwardEvaluations;

    private CountingCurve(NewtonCurve delegate) {
      this.delegate = delegate;
    }

    private void reset() {
      solves = 0;
      forwardEvaluations = 0;
    }

    @Override
    public double getXPForLevel(double level) {
      forwardEvaluations++;
      return delegate.getXPForLevel(level);
    }

    @Override
    public double computeLevelForXP(double xp, double maxError) {
      solves++;
      return delegate.computeLevelForXP(xp, maxError);
    }
  }
}
