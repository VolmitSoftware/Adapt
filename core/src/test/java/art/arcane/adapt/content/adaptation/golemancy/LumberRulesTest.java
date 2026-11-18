package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LumberRulesTest {
  private final GolemLumberCore.Config config = new GolemLumberCore.Config();

  @Test
  void logsPerActFollowTheRateAcrossActs() {
    assertThat(acts(1.0D, 4)).containsExactly(1, 1, 1, 1);
    assertThat(acts(1.5D, 4)).containsExactly(1, 2, 1, 2);
    assertThat(acts(3.0D, 2)).containsExactly(3, 3);
    assertThat(acts(0.5D, 4)).containsExactly(0, 1, 0, 1);
    assertThat(LumberRules.logsThisAct(2.0D, 0)).isZero();
  }

  @Test
  void levelValuesClampAndFallBack() {
    List<Double> rates = List.of(1.0D, 1.5D, 2.0D, 3.0D, 4.0D);

    assertThat(LumberRules.logsPerSecond(rates, 2)).isEqualTo(1.5D);
    assertThat(LumberRules.logsPerSecond(rates, 9)).isEqualTo(4.0D);
    assertThat(LumberRules.logsPerSecond(rates, 0)).isEqualTo(1.0D);
    assertThat(LumberRules.logsPerSecond(List.of(-2.0D), 1)).isEqualTo(LumberRules.MIN_LOGS_PER_SECOND);
    assertThat(LumberRules.logsPerSecond(List.of(Double.NaN), 1)).isEqualTo(LumberRules.MIN_LOGS_PER_SECOND);
    assertThat(LumberRules.logsPerSecond(List.of(1000.0D), 1)).isEqualTo(LumberRules.MAX_LOGS_PER_SECOND);
    assertThat(LumberRules.treeCap(List.of(24, 32), 2)).isEqualTo(32);
    assertThat(LumberRules.treeCap(List.of(0), 1)).isEqualTo(1);
  }

  @Test
  void ironGolemsUseTheIronCapAndEveryOtherBodyTheCopperCap() {
    assertThat(LumberRules.cap(config, GolemBody.IRON, 1)).isEqualTo(64);
    assertThat(LumberRules.cap(config, GolemBody.COPPER, 1)).isEqualTo(24);
    assertThat(LumberRules.cap(config, GolemBody.FROST, 3)).isEqualTo(48);
    assertThat(LumberRules.cap(config, GolemBody.IRON, 5)).isEqualTo(256);
  }

  @Test
  void jobsAllowOnlyTheBodiesWhoseCapHoldsTheTree() {
    assertThat(LumberRules.bodies(config, 1, 24)).isEqualTo(EnumSet.allOf(GolemBody.class));
    assertThat(LumberRules.bodies(config, 1, 25)).containsExactly(GolemBody.IRON);
    assertThat(LumberRules.bodies(config, 1, 65)).isEmpty();
    assertThat(LumberRules.bodies(config, 5, 96)).isEqualTo(EnumSet.allOf(GolemBody.class));
  }

  @Test
  void eachLevelUnlocksItsStep() {
    LumberRules.Plan one = LumberRules.plan(config, 1);
    LumberRules.Plan two = LumberRules.plan(config, 2);
    LumberRules.Plan three = LumberRules.plan(config, 3);
    LumberRules.Plan four = LumberRules.plan(config, 4);
    LumberRules.Plan five = LumberRules.plan(config, 5);

    assertThat(List.of(one.replant(), one.sweep(), one.mega(), one.nether())).containsOnly(false);
    assertThat(List.of(two.replant(), two.sweep(), two.mega(), two.nether())).containsExactly(true, false, false, false);
    assertThat(List.of(three.replant(), three.sweep(), three.mega(), three.nether())).containsExactly(true, true, false, false);
    assertThat(List.of(four.replant(), four.sweep(), four.mega(), four.nether())).containsExactly(true, true, true, false);
    assertThat(List.of(five.replant(), five.sweep(), five.mega(), five.nether())).containsOnly(true);
    assertThat(two.logsPerSecond()).isEqualTo(1.5D);
    assertThat(three.largestCap()).isEqualTo(128);
    assertThat(four.reach(64)).isEqualTo(new LumberTrees.Reach(true, false, 64));
    assertThat(LumberRules.plan(config, 0).replant()).isFalse();
  }

  private static List<Integer> acts(double perSecond, int count) {
    List<Integer> cuts = new ArrayList<>(count);
    for (int act = 1; act <= count; act++) {
      cuts.add(LumberRules.logsThisAct(perSecond, act));
    }
    return cuts;
  }
}
