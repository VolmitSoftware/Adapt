package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxeOreLogicTest {
  private static Id id(String path) {
    return Id.minecraft(path);
  }

  @Test
  void veinFamilyNormalizesDeepslateVariantsToStoneVariants() {
    assertThat(PickaxeVein.veinFamily(id("deepslate_iron_ore"))).isEqualTo(id("iron_ore"));
    assertThat(PickaxeVein.veinFamily(id("deepslate_gold_ore"))).isEqualTo(id("gold_ore"));
    assertThat(PickaxeVein.veinFamily(id("deepslate_copper_ore"))).isEqualTo(id("copper_ore"));
    assertThat(PickaxeVein.veinFamily(id("deepslate_diamond_ore"))).isEqualTo(id("diamond_ore"));
    assertThat(PickaxeVein.veinFamily(id("deepslate_redstone_ore"))).isEqualTo(id("redstone_ore"));
  }

  @Test
  void veinFamilyChainsAcrossStoneDeepslateBoundary() {
    assertThat(PickaxeVein.veinFamily(id("iron_ore")))
        .isEqualTo(PickaxeVein.veinFamily(id("deepslate_iron_ore")));
  }

  @Test
  void veinFamilyLeavesNonDeepslateMaterialsUntouched() {
    assertThat(PickaxeVein.veinFamily(id("iron_ore"))).isEqualTo(id("iron_ore"));
    assertThat(PickaxeVein.veinFamily(id("nether_gold_ore"))).isEqualTo(id("nether_gold_ore"));
    assertThat(PickaxeVein.veinFamily(id("ancient_debris"))).isEqualTo(id("ancient_debris"));
    assertThat(PickaxeVein.veinFamily(id("obsidian"))).isEqualTo(id("obsidian"));
    assertThat(PickaxeVein.veinFamily(id("deepslate"))).isEqualTo(id("deepslate"));
  }

  @Test
  void veinminableIncludesOresObsidianAndAncientDebris() {
    assertThat(PickaxeVein.isVeinminable(id("iron_ore"))).isTrue();
    assertThat(PickaxeVein.isVeinminable(id("deepslate_iron_ore"))).isTrue();
    assertThat(PickaxeVein.isVeinminable(id("obsidian"))).isTrue();
    assertThat(PickaxeVein.isVeinminable(id("ancient_debris"))).isTrue();
    assertThat(PickaxeVein.isVeinminable(id("stone"))).isFalse();
    assertThat(PickaxeVein.isVeinminable(id("deepslate"))).isFalse();
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
    assertThat(PickaxeAutosmelt.getRawDropFor(id("iron_ore"))).isEqualTo(id("raw_iron"));
    assertThat(PickaxeAutosmelt.getRawDropFor(id("deepslate_gold_ore"))).isEqualTo(id("raw_gold"));
    assertThat(PickaxeAutosmelt.getRawDropFor(id("copper_ore"))).isEqualTo(id("raw_copper"));
    assertThat(PickaxeAutosmelt.getRawDropFor(id("diamond_ore"))).isNull();
    assertThat(PickaxeAutosmelt.isNativeRawDrop(id("iron_ore"), id("raw_iron"))).isTrue();
    assertThat(PickaxeAutosmelt.isNativeRawDrop(id("iron_ore"), id("diamond"))).isFalse();
    assertThat(PickaxeAutosmelt.isNativeRawDrop(id("diamond_ore"), id("diamond"))).isFalse();
  }

  @Test
  void repairRhythmRestoresVisibleDamageAndClampsAtPristine() {
    assertThat(PickaxeRepair.repairedDamage(100, 1)).isEqualTo(99);
    assertThat(PickaxeRepair.repairedDamage(100, 2)).isEqualTo(98);
    assertThat(PickaxeRepair.repairedDamage(1, 2)).isZero();
    assertThat(PickaxeRepair.repairedDamage(5, -2)).isEqualTo(5);
  }
}
