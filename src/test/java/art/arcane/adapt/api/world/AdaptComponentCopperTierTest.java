package art.arcane.adapt.api.world;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptComponentCopperTierTest {
  private final AdaptComponent component = new AdaptComponent() {
  };

  private ItemStack item(Material material) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(material);
    return stack;
  }

  @Test
  void copperToolsAreRecognizedByToolTypeChecks() {
    assertThat(component.isSword(item(Material.COPPER_SWORD))).isTrue();
    assertThat(component.isAxe(item(Material.COPPER_AXE))).isTrue();
    assertThat(component.isPickaxe(item(Material.COPPER_PICKAXE))).isTrue();
    assertThat(component.isShovel(item(Material.COPPER_SHOVEL))).isTrue();
    assertThat(component.isHoe(item(Material.COPPER_HOE))).isTrue();
    assertThat(component.isTool(item(Material.COPPER_SWORD))).isTrue();
    assertThat(component.isMelee(item(Material.COPPER_SWORD))).isTrue();
  }

  @Test
  void copperArmorIsRecognizedByArmorTypeChecks() {
    assertThat(component.isHelmet(item(Material.COPPER_HELMET))).isTrue();
    assertThat(component.isChestplate(item(Material.COPPER_CHESTPLATE))).isTrue();
    assertThat(component.isLeggings(item(Material.COPPER_LEGGINGS))).isTrue();
    assertThat(component.isBoots(item(Material.COPPER_BOOTS))).isTrue();
  }

  @Test
  void nonToolMaterialsAreStillRejected() {
    ItemStack ingot = item(Material.COPPER_INGOT);
    assertThat(component.isSword(ingot)).isFalse();
    assertThat(component.isAxe(ingot)).isFalse();
    assertThat(component.isPickaxe(ingot)).isFalse();
    assertThat(component.isShovel(ingot)).isFalse();
    assertThat(component.isHoe(ingot)).isFalse();
  }
}
