package art.arcane.adapt.content.adaptation.pickaxe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PickaxePlayerPreferencesTest {
  @Test
  void materialPresetsExcludeGearAndKeepDeepslateOreVariants() {
    assertThat(PickaxePreferences.Materials.GEMS.accepts(Material.DIAMOND_PICKAXE)).isFalse();
    assertThat(PickaxePreferences.Materials.GEMS.accepts(Material.DEEPSLATE_DIAMOND_ORE)).isTrue();
    assertThat(PickaxePreferences.Materials.IRON.accepts(Material.DEEPSLATE_IRON_ORE)).isTrue();
    assertThat(PickaxePreferences.Materials.IRON.accepts(Material.GOLD_ORE)).isFalse();
    assertThat(PickaxePreferences.Materials.IRON.accepts(Material.IRON_CHESTPLATE)).isFalse();
  }

  @Test
  void durabilityReserveChecksFullCostAgainstCustomMaximumWithoutMutatingItem() {
    ItemStack tool = mock(ItemStack.class);
    Damageable damage = mock(Damageable.class);
    when(tool.getItemMeta()).thenReturn(damage);
    when(damage.hasMaxDamage()).thenReturn(true);
    when(damage.getMaxDamage()).thenReturn(100);
    when(damage.getDamage()).thenReturn(70);
    assertThat(PickaxePreferences.Reserve.QUARTER.permits(tool, 5)).isTrue();
    assertThat(PickaxePreferences.Reserve.QUARTER.permits(tool, 6)).isFalse();
    assertThat(PickaxePreferences.Reserve.HALF.permits(tool, 1)).isFalse();
    assertThat(PickaxePreferences.Reserve.NONE.permits(tool, 100)).isTrue();
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
