package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IronFrameRulesTest {
  private static final List<Integer> HEALTH = List.of(10, 20, 30, 40, 50);
  private static final List<Integer> ARMOR = List.of(2, 4, 6, 8, 10);
  private static final List<Double> ATTACK = List.of(1.5D, 3D, 4.5D, 6D, 7.5D);
  private static final List<Integer> INGOT_HEAL = List.of(5, 10, 15, 20, 25);
  private static final List<Integer> SWEEP_TARGETS = List.of(0, 0, 1, 1, 2);

  @Test
  void theFrameAddsHealthArmorAndAttackByLevel() {
    assertThat(IronFrameRules.frameDeltas(HEALTH, ARMOR, ATTACK, 0)).isEmpty();
    assertThat(IronFrameRules.frameDeltas(HEALTH, ARMOR, ATTACK, 1)).containsExactly(
        GolemStatDelta.add(GolemStat.MAX_HEALTH, 10D), GolemStatDelta.add(GolemStat.ARMOR, 2D), GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 1.5D));
    assertThat(IronFrameRules.frameDeltas(HEALTH, ARMOR, ATTACK, 3)).containsExactly(
        GolemStatDelta.add(GolemStat.MAX_HEALTH, 30D), GolemStatDelta.add(GolemStat.ARMOR, 6D), GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 4.5D));
    assertThat(IronFrameRules.frameDeltas(HEALTH, ARMOR, ATTACK, 5)).containsExactly(
        GolemStatDelta.add(GolemStat.MAX_HEALTH, 50D), GolemStatDelta.add(GolemStat.ARMOR, 10D), GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 7.5D));
    assertThat(IronFrameRules.frameDeltas(HEALTH, ARMOR, ATTACK, 9)).isEqualTo(IronFrameRules.frameDeltas(HEALTH, ARMOR, ATTACK, 5));
    assertThat(IronFrameRules.frameDeltas(List.of(-4), Arrays.asList((Integer) null), List.of(Double.NaN), 1)).isEmpty();
  }

  @Test
  void colossusAddsItsAttackMultiplierOnlyWhileInTheFormAtTheUnlockLevel() {
    assertThat(IronFrameRules.colossusDeltas(true, 5, 5, 0.20D)).containsExactly(GolemStatDelta.multiplyBase(GolemStat.ATTACK_DAMAGE, 0.20D));
    assertThat(IronFrameRules.colossusDeltas(false, 5, 5, 0.20D)).isEmpty();
    assertThat(IronFrameRules.colossusDeltas(true, 4, 5, 0.20D)).isEmpty();
    assertThat(IronFrameRules.colossusDeltas(true, 5, 5, 0D)).isEmpty();
    assertThat(IronFrameRules.colossusDeltas(true, 5, 5, Double.NaN)).isEmpty();
  }

  @Test
  void ingotRepairsHealTheLevelsBonus() {
    assertThat(IronFrameRules.ingotHealBonus(INGOT_HEAL, 0)).isZero();
    assertThat(IronFrameRules.ingotHealBonus(INGOT_HEAL, 1)).isEqualTo(5D);
    assertThat(IronFrameRules.ingotHealBonus(INGOT_HEAL, 5)).isEqualTo(25D);
    assertThat(IronFrameRules.ingotHealBonus(List.of(-3), 1)).isZero();
  }

  @Test
  void sweepingFistsHitsTheLevelsTargetCountFromItsUnlockLevel() {
    assertThat(IronFrameRules.sweepTargets(SWEEP_TARGETS, 2, 3)).isZero();
    assertThat(IronFrameRules.sweepTargets(SWEEP_TARGETS, 3, 3)).isEqualTo(1);
    assertThat(IronFrameRules.sweepTargets(SWEEP_TARGETS, 4, 3)).isEqualTo(1);
    assertThat(IronFrameRules.sweepTargets(SWEEP_TARGETS, 5, 3)).isEqualTo(2);
    assertThat(IronFrameRules.sweepTargets(SWEEP_TARGETS, 5, 6)).isZero();
    assertThat(IronFrameRules.sweepTargets(List.of(-1, -1, -1), 3, 3)).isZero();
    assertThat(IronFrameRules.sweepDamage(12D, 0.5D)).isEqualTo(6D);
    assertThat(IronFrameRules.sweepDamage(12D, -1D)).isZero();
    assertThat(IronFrameRules.sweepDamage(12D, Double.NaN)).isZero();
    assertThat(IronFrameRules.sweepDamage(Double.NaN, 0.5D)).isZero();
  }

  @Test
  void sweepTargetsAreTheNearestDamageableHostilesOtherThanTheVictimUpToTheCap() {
    List<IronFrameRules.SweepCandidate<String>> candidates = new ArrayList<>(List.of(
        new IronFrameRules.SweepCandidate<>("victim", true, true, 0D),
        new IronFrameRules.SweepCandidate<>("golem", true, false, 1D),
        new IronFrameRules.SweepCandidate<>("far zombie", false, true, 3.9D),
        new IronFrameRules.SweepCandidate<>("cow", false, false, 0.5D),
        new IronFrameRules.SweepCandidate<>("servant", false, true, 0.2D),
        new IronFrameRules.SweepCandidate<>("near zombie", false, true, 1.5D),
        new IronFrameRules.SweepCandidate<>("husk", false, true, 2.5D),
        new IronFrameRules.SweepCandidate<>("outside", false, true, 4.1D)));
    Set<String> friendly = Set.of("servant");
    List<String> checked = new ArrayList<>();

    List<String> two = IronFrameRules.sweepVictims(candidates, 2D, 2, target -> {
      checked.add(target);
      return !friendly.contains(target);
    });

    assertThat(two).containsExactly("near zombie", "husk");
    assertThat(checked).containsExactly("servant", "near zombie", "husk");
    assertThat(IronFrameRules.sweepVictims(candidates, 2D, 1, target -> !friendly.contains(target))).containsExactly("near zombie");
    assertThat(IronFrameRules.sweepVictims(candidates, 2D, 5, target -> !friendly.contains(target))).containsExactly("near zombie", "husk", "far zombie");
    assertThat(IronFrameRules.sweepVictims(candidates, 2D, 0, target -> true)).isEmpty();
    assertThat(IronFrameRules.sweepVictims(candidates, 2D, 2, target -> false)).isEmpty();
  }

  @Test
  void theSweepRadiusLoadsBetweenZeroAndEightBlocks() {
    assertThat(IronFrameRules.sweepRadius(2D)).isEqualTo(2D);
    assertThat(IronFrameRules.sweepRadius(30D)).isEqualTo(8D);
    assertThat(IronFrameRules.sweepRadius(-1D)).isZero();
    assertThat(IronFrameRules.sweepRadius(Double.NaN)).isEqualTo(2D);
  }

  @Test
  void colossusTogglesGrowAtTheUnlockLevelAndAlwaysAllowShrinkingBack() {
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, false, false)).isEqualTo(IronFrameRules.ColossusToggle.GROW);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, true, false)).isEqualTo(IronFrameRules.ColossusToggle.SHRINK);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 4, 5, false, false)).isEqualTo(IronFrameRules.ColossusToggle.UNAVAILABLE);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 2, 5, true, false)).isEqualTo(IronFrameRules.ColossusToggle.SHRINK);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, false, true)).isEqualTo(IronFrameRules.ColossusToggle.BUSY);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, true, true)).isEqualTo(IronFrameRules.ColossusToggle.BUSY);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 0, 0, false, false)).isEqualTo(IronFrameRules.ColossusToggle.UNAVAILABLE);
    for (GolemBody body : List.of(GolemBody.FROST, GolemBody.COPPER, GolemBody.BONE)) {
      assertThat(IronFrameRules.colossusToggle(body, 5, 5, false, false)).as("toggle for %s", body).isEqualTo(IronFrameRules.ColossusToggle.UNAVAILABLE);
      assertThat(IronFrameRules.colossusToggle(body, 5, 5, true, false)).as("toggle for %s in form", body).isEqualTo(IronFrameRules.ColossusToggle.UNAVAILABLE);
    }
  }

  @Test
  void resizesLastTheirGrowTicksAndCountAsBusyUntilThen() {
    assertThat(IronFrameRules.growTicks(20)).isEqualTo(20);
    assertThat(IronFrameRules.growTicks(0)).isEqualTo(1);
    IronFrameRules.Resize resize = IronFrameRules.resize(7L, 1_000L, 20);

    assertThat(resize.token()).isEqualTo(7L);
    assertThat(IronFrameRules.resizing(null, 1_000L)).isFalse();
    assertThat(IronFrameRules.resizing(resize, 1_000L)).isTrue();
    assertThat(IronFrameRules.resizing(resize, 2_999L)).isTrue();
    assertThat(IronFrameRules.resizing(resize, 3_000L)).isFalse();
  }
}
