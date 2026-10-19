package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GolemRuneLayoutTest {
  private static final GolemRuneLayout COPPER_LEVEL_THREE = new GolemRuneLayout(3, List.of(1, 1, 3, 5), 2, List.of(1, 3, 5), Set.of());
  private static final GolemRuneLayout FROST_LEVEL_FIVE = new GolemRuneLayout(3, List.of(1, 3, 5), 3, List.of(1, 3, 5), Set.of(GolemRuneType.EMBER));

  @Test
  void slotsAndTiersReportTheLevelThatUnlocksThem() {
    assertThat(COPPER_LEVEL_THREE.maxSlots()).isEqualTo(4);
    assertThat(COPPER_LEVEL_THREE.slotUnlockLevel(0)).isEqualTo(1);
    assertThat(COPPER_LEVEL_THREE.slotUnlockLevel(2)).isEqualTo(3);
    assertThat(COPPER_LEVEL_THREE.slotUnlockLevel(3)).isEqualTo(5);
    assertThat(COPPER_LEVEL_THREE.slotUnlockLevel(4)).isZero();
    assertThat(COPPER_LEVEL_THREE.tierUnlockLevel(2)).isEqualTo(3);
    assertThat(COPPER_LEVEL_THREE.tierUnlockLevel(3)).isEqualTo(5);
    assertThat(COPPER_LEVEL_THREE.tierUnlockLevel(4)).isZero();
    assertThat(FROST_LEVEL_FIVE.blocks(GolemRuneType.EMBER)).isTrue();
    assertThat(FROST_LEVEL_FIVE.blocks(GolemRuneType.TIDE)).isFalse();
  }

  @Test
  void theLayoutClampsOpenSlotsAndTiersToWhatItCanHold() {
    GolemRuneLayout layout = new GolemRuneLayout(9, List.of(1, 3), 7, List.of(1, 3, 5), Set.of());

    assertThat(layout.unlockedSlots()).isEqualTo(2);
    assertThat(layout.maxTier()).isEqualTo(GolemRuneType.MAX_TIER);
    assertThat(GolemRuneLayout.NONE.maxSlots()).isZero();
    assertThat(GolemRuneLayout.NONE.unlockedSlots()).isZero();
  }

  @Test
  void eachSlottedRuneIsActiveOrInertForOneReason() {
    List<GolemRune> runes = List.of(rune(GolemRuneType.SWIFTNESS, 2), rune(GolemRuneType.MIGHT, 3), rune(GolemRuneType.SWIFTNESS, 1),
        rune(GolemRuneType.BULWARK, 1));

    assertThat(COPPER_LEVEL_THREE.status(runes, 0)).isEqualTo(GolemRuneLayout.Status.ACTIVE);
    assertThat(COPPER_LEVEL_THREE.status(runes, 1)).isEqualTo(GolemRuneLayout.Status.OVER_TIER);
    assertThat(COPPER_LEVEL_THREE.status(runes, 2)).isEqualTo(GolemRuneLayout.Status.DUPLICATE);
    assertThat(COPPER_LEVEL_THREE.status(runes, 3)).isEqualTo(GolemRuneLayout.Status.LOCKED_SLOT);
    assertThat(FROST_LEVEL_FIVE.status(List.of(rune(GolemRuneType.EMBER, 1)), 0)).isEqualTo(GolemRuneLayout.Status.BLOCKED);
  }

  @Test
  void onlyActiveRunesCountTowardTheirAspect() {
    List<GolemRune> runes = List.of(rune(GolemRuneType.TIDE, 2), rune(GolemRuneType.MIGHT, 3), rune(GolemRuneType.TIDE, 1),
        rune(GolemRuneType.ORDER, 1));

    Map<GolemRuneType, Integer> active = COPPER_LEVEL_THREE.active(runes);

    assertThat(active).containsExactlyEntriesOf(Map.of(GolemRuneType.TIDE, 2));
    assertThat(GolemRuneLayout.NONE.active(runes)).isEmpty();
  }

  @Test
  void slottingNeedsAnOpenSlotANewAspectAndABodyThatAllowsIt() {
    List<GolemRune> two = List.of(rune(GolemRuneType.SWIFTNESS, 1), rune(GolemRuneType.MIGHT, 1));
    List<GolemRune> three = List.of(rune(GolemRuneType.SWIFTNESS, 1), rune(GolemRuneType.MIGHT, 1), rune(GolemRuneType.TIDE, 1));

    assertThat(COPPER_LEVEL_THREE.refuse(two, GolemRuneType.ORDER)).isEmpty();
    assertThat(COPPER_LEVEL_THREE.refuse(two, GolemRuneType.MIGHT)).contains(GolemRuneLayout.Refusal.DUPLICATE);
    assertThat(COPPER_LEVEL_THREE.refuse(three, GolemRuneType.ORDER)).contains(GolemRuneLayout.Refusal.NO_SLOT);
    assertThat(FROST_LEVEL_FIVE.refuse(List.of(), GolemRuneType.EMBER)).contains(GolemRuneLayout.Refusal.BLOCKED);
    assertThat(GolemRuneLayout.NONE.refuse(List.of(), GolemRuneType.ORDER)).contains(GolemRuneLayout.Refusal.NO_SLOT);
  }

  private static GolemRune rune(GolemRuneType aspect, int tier) {
    return new GolemRune(aspect, tier);
  }
}
