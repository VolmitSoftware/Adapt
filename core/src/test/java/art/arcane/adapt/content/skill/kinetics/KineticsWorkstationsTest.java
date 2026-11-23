package art.arcane.adapt.content.skill.kinetics;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsWorkstationsTest {
  @Test
  void anvilXpScalesWithLevelsSpent() {
    assertThat(KineticsWorkstations.anvilXp(30, 25, 15D)).isEqualTo(75D);
    assertThat(KineticsWorkstations.anvilXp(30, 1, 15D)).isEqualTo(435D);
  }

  @Test
  void anvilTakesThatSpentNothingPayNothing() {
    assertThat(KineticsWorkstations.anvilXp(30, 30, 15D)).isZero();
    assertThat(KineticsWorkstations.anvilXp(30, 31, 15D)).isZero();
    assertThat(KineticsWorkstations.anvilXp(30, 25, Double.NaN)).isZero();
  }

  @Test
  void grindstoneCountsRemovedEnchantLevelsWithoutCurses() {
    FakeItem sword = FakeItem.tool("iron_sword", 250).enchant("sharpness", 5).enchant("unbreaking", 3)
        .enchant("vanishing_curse", 1);
    FakeItem result = FakeItem.tool("iron_sword", 250).enchant("vanishing_curse", 1);

    assertThat(KineticsWorkstations.removedEnchantLevels(sword, FakeItem.empty(), result)).isEqualTo(8);
    assertThat(KineticsWorkstations.grindstoneXp(8, 10D)).isEqualTo(80D);
  }

  @Test
  void grindstoneCountsStoredBookEnchantments() {
    FakeItem book = FakeItem.of("enchanted_book");
    book.setStoredEnchantment(Id.minecraft("mending"), 1);
    book.setStoredEnchantment(Id.minecraft("efficiency"), 4);

    assertThat(KineticsWorkstations.removedEnchantLevels(FakeItem.empty(), book, FakeItem.of("book"))).isEqualTo(5);
  }

  @Test
  void repairWithoutEnchantmentsPaysNothing() {
    FakeItem pick = FakeItem.tool("iron_pickaxe", 250);
    assertThat(KineticsWorkstations.removedEnchantLevels(pick, pick, pick)).isZero();
    assertThat(KineticsWorkstations.grindstoneXp(0, 10D)).isZero();
  }
}
