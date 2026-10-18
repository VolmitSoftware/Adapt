package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCraftingTitheCapTest {
  @Test
  void shiftCraftXpStopsAtTheCraftsATitheLetsThePlayerAfford() {
    assertThat(SkillCrafting.tithedCraftCap(1, 1, 1, 64)).isEqualTo(1);
    assertThat(SkillCrafting.tithedCraftCap(7, 3, 4, 64)).isEqualTo(8);
    assertThat(SkillCrafting.tithedCraftCap(0, 2, 1, 64)).isZero();
    assertThat(SkillCrafting.tithedCraftCap(100, 1, 4, 12)).isEqualTo(12);
  }

  @Test
  void recipesWithoutATitheKeepTheFullShiftCraftAmount() {
    assertThat(SkillCrafting.tithedCraftCap(1, 0, 1, 64)).isEqualTo(64);
    assertThat(SkillCrafting.tithedCraftCap(0, 0, 4, 64)).isEqualTo(64);
    assertThat(SkillCrafting.tithedCraftCap(5, 1, 0, 64)).isEqualTo(64);
  }
}
