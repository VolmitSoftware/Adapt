package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRuneType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RunicAugmentsRulesTest {
  private static final List<Integer> EXTRA_SLOTS = List.of(0, 0, 1, 1, 2);
  private static final List<Integer> MAX_TIERS = List.of(1, 1, 2, 2, 3);

  @Test
  void extraSlotsAndTheHighestTierFollowTheLevelTable() {
    assertThat(perLevel(level -> RunicAugmentsRules.extraSlots(EXTRA_SLOTS, level))).containsExactly(0, 0, 1, 1, 2);
    assertThat(perLevel(level -> RunicAugmentsRules.maxTier(MAX_TIERS, level))).containsExactly(1, 1, 2, 2, 3);
    assertThat(RunicAugmentsRules.extraSlots(EXTRA_SLOTS, 0)).isZero();
    assertThat(RunicAugmentsRules.maxTier(MAX_TIERS, 0)).isZero();
    assertThat(RunicAugmentsRules.maxTier(List.of(9), 1)).isEqualTo(GolemRuneType.MAX_TIER);
    assertThat(RunicAugmentsRules.extraSlots(List.of(-2), 1)).isZero();
  }

  @Test
  void eachBodyKeepsItsBaseSlotsAndGainsExtraSlotsByLevel() {
    GolemRuneLayout frost = RunicAugmentsRules.layout(1, EXTRA_SLOTS, MAX_TIERS, 5, Set.of(GolemRuneType.EMBER), 0);
    GolemRuneLayout copper = RunicAugmentsRules.layout(2, EXTRA_SLOTS, MAX_TIERS, 5, Set.of(), 3);
    GolemRuneLayout ironTwo = RunicAugmentsRules.layout(3, EXTRA_SLOTS, MAX_TIERS, 5, Set.of(), 2);
    GolemRuneLayout ironFive = RunicAugmentsRules.layout(3, EXTRA_SLOTS, MAX_TIERS, 5, Set.of(), 5);

    assertThat(frost.unlockedSlots()).isZero();
    assertThat(frost.slotUnlockLevels()).containsExactly(1, 3, 5);
    assertThat(frost.maxTier()).isZero();
    assertThat(frost.tierUnlockLevels()).containsExactly(1, 3, 5);
    assertThat(frost.blocks(GolemRuneType.EMBER)).isTrue();
    assertThat(copper.unlockedSlots()).isEqualTo(3);
    assertThat(copper.slotUnlockLevels()).containsExactly(1, 1, 3, 5);
    assertThat(copper.maxTier()).isEqualTo(2);
    assertThat(ironTwo.unlockedSlots()).isEqualTo(3);
    assertThat(ironTwo.maxTier()).isEqualTo(1);
    assertThat(ironFive.unlockedSlots()).isEqualTo(5);
    assertThat(ironFive.slotUnlockLevels()).containsExactly(1, 1, 1, 3, 5);
    assertThat(ironFive.maxTier()).isEqualTo(3);
  }

  @Test
  void slotsNeverOutgrowOneMenuRowAndBodiesWithoutSlotsGetNone() {
    GolemRuneLayout crowded = RunicAugmentsRules.layout(8, List.of(0, 5), MAX_TIERS, 2, Set.of(), 2);
    GolemRuneLayout empty = RunicAugmentsRules.layout(0, List.of(0, 0, 0, 0, 0), MAX_TIERS, 5, Set.of(), 5);

    assertThat(crowded.maxSlots()).isEqualTo(GolemRuneLayout.MAX_SLOTS);
    assertThat(crowded.unlockedSlots()).isEqualTo(GolemRuneLayout.MAX_SLOTS);
    assertThat(empty.maxSlots()).isZero();
    assertThat(empty.unlockedSlots()).isZero();
  }

  @Test
  void aTierNoLevelReachesUnlocksAtNoLevel() {
    GolemRuneLayout capped = RunicAugmentsRules.layout(1, EXTRA_SLOTS, List.of(1, 1, 2, 2, 2), 5, Set.of(), 5);

    assertThat(capped.tierUnlockLevels()).containsExactly(1, 3, 0);
  }

  @Test
  void runeBodyBlocksPairAnAspectWithABodyAndWarnAboutTheRest() {
    List<String> warnings = new ArrayList<>();

    Map<GolemBody, Set<GolemRuneType>> blocks = RunicAugmentsRules.bodyBlocks(
        List.of("ember:frost", " TIDE : Iron ", "lava:frost", "ember", "ember:stone"), warnings::add);

    assertThat(blocks).containsOnlyKeys(GolemBody.FROST, GolemBody.IRON);
    assertThat(blocks.get(GolemBody.FROST)).containsExactly(GolemRuneType.EMBER);
    assertThat(blocks.get(GolemBody.IRON)).containsExactly(GolemRuneType.TIDE);
    assertThat(warnings).hasSize(3);
    assertThat(warnings.get(0)).contains("lava:frost");
    assertThat(warnings.get(1)).contains("ember");
    assertThat(warnings.get(2)).contains("ember:stone");
    assertThat(RunicAugmentsRules.bodyBlocks(null, warnings::add)).isEmpty();
  }

  @Test
  void perTierValuesUseTheTierAsTheirIndex() {
    assertThat(RunicAugmentsRules.atTier(List.of(2, 4, 6), 2, 0)).isEqualTo(4);
    assertThat(RunicAugmentsRules.atTier(List.of(2, 4, 6), 0, 0)).isZero();
    assertThat(RunicAugmentsRules.atTier(List.of(2, 4), 3, 0)).isEqualTo(4);
  }

  private static List<Integer> perLevel(IntUnaryOperator value) {
    return IntStream.rangeClosed(1, 5).map(value).boxed().toList();
  }
}
