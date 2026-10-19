package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

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
  void colossusMultipliesTheTotalAttackAtTheUnlockLevel() {
    GolemStatDelta bonus = GolemStatDelta.multiplyTotal(GolemStat.ATTACK_DAMAGE, 0.20D);

    assertThat(bonus.operation()).isEqualTo(AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    assertThat(IronFrameRules.colossusDeltas(5, 5, 0.20D)).containsExactly(bonus);
    assertThat(IronFrameRules.colossusDeltas(4, 5, 0.20D)).isEmpty();
    assertThat(IronFrameRules.colossusDeltas(5, 5, 0D)).isEmpty();
    assertThat(IronFrameRules.colossusDeltas(5, 5, Double.NaN)).isEmpty();
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
  void colossusTogglesNeedTheUnlockLevelAndRoomToGrow() {
    AtomicInteger roomChecks = new AtomicInteger();
    BooleanSupplier room = () -> {
      roomChecks.incrementAndGet();
      return true;
    };

    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, false, false, room)).isEqualTo(GolemColossus.Toggle.GROW);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, false, false, () -> false)).isEqualTo(GolemColossus.Toggle.NO_ROOM);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, true, false, room)).isEqualTo(GolemColossus.Toggle.SHRINK);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, false, true, room)).isEqualTo(GolemColossus.Toggle.BUSY);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 5, 5, true, true, room)).isEqualTo(GolemColossus.Toggle.BUSY);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 4, 5, false, false, room)).isEqualTo(GolemColossus.Toggle.UNAVAILABLE);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 4, 5, true, false, room)).isEqualTo(GolemColossus.Toggle.UNAVAILABLE);
    assertThat(IronFrameRules.colossusToggle(GolemBody.IRON, 0, 0, false, false, room)).isEqualTo(GolemColossus.Toggle.UNAVAILABLE);
    for (GolemBody body : List.of(GolemBody.FROST, GolemBody.COPPER, GolemBody.BONE)) {
      assertThat(IronFrameRules.colossusToggle(body, 5, 5, false, false, room)).as("toggle for %s", body).isEqualTo(GolemColossus.Toggle.UNAVAILABLE);
    }
    assertThat(roomChecks).hasValue(1);
  }

  @Test
  void aGolemInColossusFormOutgrowsItWhenTheOwnerDropsBelowTheUnlockLevel() {
    assertThat(IronFrameRules.outgrown(true, 4, 5)).isTrue();
    assertThat(IronFrameRules.outgrown(true, 0, 5)).isTrue();
    assertThat(IronFrameRules.outgrown(true, 5, 5)).isFalse();
    assertThat(IronFrameRules.outgrown(false, 0, 5)).isFalse();
  }

  @Test
  void theColossusScaleLoadsWithinASaneRange() {
    assertThat(IronFrameRules.colossusScale(1D)).isEqualTo(1D);
    assertThat(IronFrameRules.colossusScale(5D)).isEqualTo(1.5D);
    assertThat(IronFrameRules.colossusScale(0.1D)).isEqualTo(0.36D);
    assertThat(IronFrameRules.colossusScale(Double.NaN)).isEqualTo(1D);
  }

  @Test
  void theClearanceBoxIsAnIronGolemAtTheColossusScaleStandingOnTheGolemsFeet() {
    BoundingBox full = IronFrameRules.colossusBox(10.5D, 64D, -3.5D, 1D);
    BoundingBox half = IronFrameRules.colossusBox(0D, 0D, 0D, 0.5D);

    assertThat(full.getWidthX()).isCloseTo(1.4D - 2D * IronFrameRules.CLEARANCE_INSET, offset(1e-9));
    assertThat(full.getWidthZ()).isCloseTo(1.4D - 2D * IronFrameRules.CLEARANCE_INSET, offset(1e-9));
    assertThat(full.getMinY()).isCloseTo(64D + IronFrameRules.CLEARANCE_INSET, offset(1e-9));
    assertThat(full.getMaxY()).isCloseTo(64D + 2.7D, offset(1e-9));
    assertThat(full.getCenterX()).isCloseTo(10.5D, offset(1e-9));
    assertThat(full.getCenterZ()).isCloseTo(-3.5D, offset(1e-9));
    assertThat(half.getMaxY()).isCloseTo(1.35D, offset(1e-9));
  }

  @Test
  void aResizeLeaksOnlyLongAfterItsChainShouldHaveEnded() {
    assertThat(IronFrameRules.growTicks(20)).isEqualTo(20);
    assertThat(IronFrameRules.growTicks(0)).isEqualTo(1);
    long deadline = IronFrameRules.leakDeadline(1_000L, 20);

    assertThat(deadline).isEqualTo(1_000L + 20 * 50L * 4L + 5_000L);
    assertThat(IronFrameRules.leaked(deadline, deadline - 1L)).isFalse();
    assertThat(IronFrameRules.leaked(deadline, deadline)).isTrue();
  }
}
