package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxePlayerPreferencesTest {
  private static Id id(String path) {
    return Id.minecraft(path);
  }

  @Test
  void materialPresetsExcludeGearAndKeepDeepslateOreVariants() {
    assertThat(PickaxePreferences.Materials.GEMS.accepts(id("diamond_pickaxe"))).isFalse();
    assertThat(PickaxePreferences.Materials.GEMS.accepts(id("deepslate_diamond_ore"))).isTrue();
    assertThat(PickaxePreferences.Materials.IRON.accepts(id("deepslate_iron_ore"))).isTrue();
    assertThat(PickaxePreferences.Materials.IRON.accepts(id("gold_ore"))).isFalse();
    assertThat(PickaxePreferences.Materials.IRON.accepts(id("iron_chestplate"))).isFalse();
  }

  @Test
  void durabilityReserveChecksFullCostAgainstCustomMaximumWithoutMutatingItem() {
    FakeItem tool = FakeItem.tool("iron_pickaxe", 100).withDamage(70);

    assertThat(PickaxePreferences.Reserve.QUARTER.permits(tool, 5)).isTrue();
    assertThat(PickaxePreferences.Reserve.QUARTER.permits(tool, 6)).isFalse();
    assertThat(PickaxePreferences.Reserve.HALF.permits(tool, 1)).isFalse();
    assertThat(PickaxePreferences.Reserve.NONE.permits(tool, 100)).isTrue();
    assertThat(tool.damage()).isEqualTo(70);
    assertThat(tool.maxDamage()).isEqualTo(100);
  }

  @Test
  void smallerTunnelsNeverExceedLearnedDimensions() {
    assertThat(PickaxePreferences.Size.THREE_BY_TWO.width(5)).isEqualTo(3);
    assertThat(PickaxePreferences.Size.THREE_BY_TWO.width(1)).isEqualTo(1);
    assertThat(PickaxePreferences.Size.THREE_BY_TWO.height(1)).isEqualTo(1);
    assertThat(PickaxePreferences.Size.ONE_BY_TWO.height(4)).isEqualTo(2);
    assertThat(PickaxePreferences.Size.FULL.height(4)).isEqualTo(4);
  }
}
