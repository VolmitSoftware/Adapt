package art.arcane.adapt.content.adaptation.pickaxe;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxeOreLogicTest {
  @Test
  void veinFamilyNormalizesDeepslateVariantsToStoneVariants() {
    assertThat(PickaxeVeinminer.veinFamily(Material.DEEPSLATE_IRON_ORE)).isEqualTo(Material.IRON_ORE);
    assertThat(PickaxeVeinminer.veinFamily(Material.DEEPSLATE_GOLD_ORE)).isEqualTo(Material.GOLD_ORE);
    assertThat(PickaxeVeinminer.veinFamily(Material.DEEPSLATE_COPPER_ORE)).isEqualTo(Material.COPPER_ORE);
    assertThat(PickaxeVeinminer.veinFamily(Material.DEEPSLATE_DIAMOND_ORE)).isEqualTo(Material.DIAMOND_ORE);
    assertThat(PickaxeVeinminer.veinFamily(Material.DEEPSLATE_REDSTONE_ORE)).isEqualTo(Material.REDSTONE_ORE);
  }

  @Test
  void veinFamilyChainsAcrossStoneDeepslateBoundary() {
    assertThat(PickaxeVeinminer.veinFamily(Material.IRON_ORE))
        .isEqualTo(PickaxeVeinminer.veinFamily(Material.DEEPSLATE_IRON_ORE));
  }

  @Test
  void veinFamilyLeavesNonDeepslateMaterialsUntouched() {
    assertThat(PickaxeVeinminer.veinFamily(Material.IRON_ORE)).isEqualTo(Material.IRON_ORE);
    assertThat(PickaxeVeinminer.veinFamily(Material.NETHER_GOLD_ORE)).isEqualTo(Material.NETHER_GOLD_ORE);
    assertThat(PickaxeVeinminer.veinFamily(Material.ANCIENT_DEBRIS)).isEqualTo(Material.ANCIENT_DEBRIS);
    assertThat(PickaxeVeinminer.veinFamily(Material.OBSIDIAN)).isEqualTo(Material.OBSIDIAN);
    assertThat(PickaxeVeinminer.veinFamily(Material.DEEPSLATE)).isEqualTo(Material.DEEPSLATE);
  }

  @Test
  void veinminableIncludesOresObsidianAndAncientDebris() {
    assertThat(PickaxeVeinminer.isVeinminable(Material.IRON_ORE)).isTrue();
    assertThat(PickaxeVeinminer.isVeinminable(Material.DEEPSLATE_IRON_ORE)).isTrue();
    assertThat(PickaxeVeinminer.isVeinminable(Material.OBSIDIAN)).isTrue();
    assertThat(PickaxeVeinminer.isVeinminable(Material.ANCIENT_DEBRIS)).isTrue();
    assertThat(PickaxeVeinminer.isVeinminable(Material.STONE)).isFalse();
    assertThat(PickaxeVeinminer.isVeinminable(Material.DEEPSLATE)).isFalse();
  }

  @Test
  void autosmeltExtraDropChanceScalesWithLevelAsAdvertised() {
    assertThat(PickaxeAutosmelt.getExtraDropChance(0)).isEqualTo(0.0D);
    assertThat(PickaxeAutosmelt.getExtraDropChance(1)).isEqualTo(0.0125D);
    assertThat(PickaxeAutosmelt.getExtraDropChance(4)).isEqualTo(0.05D);
  }

  @Test
  void autosmeltPreservesCommittedNativeDropsBeforeApplyingItsOwnBonus() {
    assertThat(PickaxeAutosmelt.getCommittedSmeltAmount(4, 4, 0.99D)).isEqualTo(4);
    assertThat(PickaxeAutosmelt.getCommittedSmeltAmount(4, 4, 0.049D)).isEqualTo(5);
    assertThat(PickaxeAutosmelt.getCommittedSmeltAmount(4, 0, 0.0D)).isEqualTo(4);
  }

  @Test
  void autosmeltMapsOnlyItsNativeRawOreDrop() {
    assertThat(PickaxeAutosmelt.getRawDropFor(Material.IRON_ORE)).isEqualTo(Material.RAW_IRON);
    assertThat(PickaxeAutosmelt.getRawDropFor(Material.DEEPSLATE_GOLD_ORE)).isEqualTo(Material.RAW_GOLD);
    assertThat(PickaxeAutosmelt.getRawDropFor(Material.COPPER_ORE)).isEqualTo(Material.RAW_COPPER);
    assertThat(PickaxeAutosmelt.getRawDropFor(Material.DIAMOND_ORE)).isNull();
    assertThat(PickaxeAutosmelt.isNativeRawDrop(Material.IRON_ORE, Material.RAW_IRON)).isTrue();
    assertThat(PickaxeAutosmelt.isNativeRawDrop(Material.IRON_ORE, Material.DIAMOND)).isFalse();
    assertThat(PickaxeAutosmelt.isNativeRawDrop(Material.DIAMOND_ORE, Material.DIAMOND)).isFalse();
  }
}
