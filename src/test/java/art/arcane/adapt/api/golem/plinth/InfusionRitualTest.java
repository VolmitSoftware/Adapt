package art.arcane.adapt.api.golem.plinth;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InfusionRitualTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final PlinthSpot CENTER = new PlinthSpot(WORLD, 0, 64, 0);

  private final Map<PlinthSlot, PlinthSlotItem> slots = slots();
  private final InfusionRitual ritual = new InfusionRitual(mock(Player.class), CENTER, infusion(100), slots, 0);

  @Test
  void aSteadyRitualIsNeverInterrupted() {
    assertThat(ritual.check(true, true, 0D, true, slots::get)).isEmpty();
  }

  @Test
  void thePlayerMayStandExactlyEightBlocksAway() {
    assertThat(ritual.check(true, true, 64D, true, slots::get)).isEmpty();
    assertThat(ritual.check(true, true, 64.01D, true, slots::get)).contains(InfusionRitual.Interruption.PLAYER_AWAY);
    assertThat(ritual.check(true, false, 0D, true, slots::get)).contains(InfusionRitual.Interruption.PLAYER_AWAY);
  }

  @Test
  void aLoggedOutPlayerInterruptsFirst() {
    assertThat(ritual.check(false, false, 100D, false, slot -> null)).contains(InfusionRitual.Interruption.LOGGED_OUT);
  }

  @Test
  void aBrokenStructureInterrupts() {
    assertThat(ritual.check(true, true, 0D, false, slots::get)).contains(InfusionRitual.Interruption.STRUCTURE_BROKEN);
  }

  @Test
  void anySwappedOrMissingSlotInterrupts() {
    for (PlinthSlot changed : PlinthSlot.values()) {
      Map<PlinthSlot, PlinthSlotItem> swapped = new EnumMap<>(slots);
      swapped.put(changed, PlinthSlotItem.of(UUID.randomUUID(), mock(ItemStack.class)));
      Map<PlinthSlot, PlinthSlotItem> missing = new EnumMap<>(slots);
      missing.remove(changed);

      assertThat(ritual.check(true, true, 0D, true, swapped::get)).as("swapped %s", changed).contains(InfusionRitual.Interruption.SLOT_CHANGED);
      assertThat(ritual.check(true, true, 0D, true, missing::get)).as("missing %s", changed).contains(InfusionRitual.Interruption.SLOT_CHANGED);
    }
  }

  @Test
  void theRitualIsDueOnceItsDurationElapses() {
    for (int tick = 0; tick < 99; tick++) {
      ritual.advance();
    }
    assertThat(ritual.due()).isFalse();
    assertThat(ritual.progress()).isEqualTo(0.99D);

    ritual.advance();

    assertThat(ritual.due()).isTrue();
    assertThat(ritual.progress()).isEqualTo(1D);
    ritual.advance();
    assertThat(ritual.progress()).isEqualTo(1D);
  }

  @Test
  void finishingHappensOnceSoAbortAndCompletionCannotBothRun() {
    assertThat(ritual.isFinished()).isFalse();
    assertThat(ritual.finish()).isTrue();
    assertThat(ritual.finish()).isFalse();
    assertThat(ritual.isFinished()).isTrue();
  }

  @Test
  void theRitualCoversItsFiveSpotsOnly() {
    for (PlinthSlot slot : PlinthSlot.values()) {
      assertThat(ritual.covers(CENTER.slot(slot))).isTrue();
    }
    assertThat(ritual.covers(new PlinthSpot(WORLD, 1, 64, 1))).isFalse();
    assertThat(ritual.covers(new PlinthSpot(UUID.randomUUID(), 0, 64, 0))).isFalse();
  }

  @Test
  void theLevelCostIsChargedOnlyOutsideCreative() {
    assertThat(InfusionRitual.levelCost(infusion(100), false)).isEqualTo(5);
    assertThat(InfusionRitual.levelCost(infusion(100), true)).isZero();
  }

  @Test
  void heartbeatStalenessCoversRetiredTasks() {
    InfusionRitual started = new InfusionRitual(mock(Player.class), CENTER, infusion(100), slots, 1_000L);

    assertThat(started.stale(5_999L)).isFalse();
    assertThat(started.stale(6_001L)).isTrue();
    started.pulsed(6_000L);
    assertThat(started.stale(10_999L)).isFalse();
  }

  private static Infusion infusion(int duration) {
    InfusionIngredient any = item -> true;
    return new Infusion("might_2", NamespacedKey.fromString("golemancy:rune/might/2"), () -> mock(ItemStack.class), any, List.of(any, any, any, any),
        5, duration, InfusionRecipe.RUNIC_AUGMENTS, 3);
  }

  private static Map<PlinthSlot, PlinthSlotItem> slots() {
    Map<PlinthSlot, PlinthSlotItem> slots = new EnumMap<>(PlinthSlot.class);
    for (PlinthSlot slot : PlinthSlot.values()) {
      slots.put(slot, PlinthSlotItem.of(UUID.randomUUID(), mock(ItemStack.class)));
    }
    return slots;
  }
}
