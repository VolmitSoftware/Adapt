package art.arcane.adapt.api.world;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptComponentSpearTest {
  private final AdaptComponent component = new AdaptComponent() {
  };

  private ItemStack item(Material material) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(material);
    return stack;
  }

  @Test
  void everySpearTierIsRecognized() {
    assertThat(component.isSpear(item(Material.WOODEN_SPEAR))).isTrue();
    assertThat(component.isSpear(item(Material.STONE_SPEAR))).isTrue();
    assertThat(component.isSpear(item(Material.COPPER_SPEAR))).isTrue();
    assertThat(component.isSpear(item(Material.IRON_SPEAR))).isTrue();
    assertThat(component.isSpear(item(Material.GOLDEN_SPEAR))).isTrue();
    assertThat(component.isSpear(item(Material.DIAMOND_SPEAR))).isTrue();
    assertThat(component.isSpear(item(Material.NETHERITE_SPEAR))).isTrue();
  }

  @Test
  void nonSpearItemsAreRejected() {
    assertThat(component.isSpear(item(Material.TRIDENT))).isFalse();
    assertThat(component.isSpear(item(Material.MACE))).isFalse();
    assertThat(component.isSpear(item(Material.AIR))).isFalse();
    assertThat(component.isSpear(null)).isFalse();
  }

  @Test
  void spearsCountAsToolsLikeMaces() {
    assertThat(component.isTool(item(Material.IRON_SPEAR))).isTrue();
    assertThat(component.isTool(item(Material.WOODEN_SPEAR))).isTrue();
    assertThat(component.isTool(item(Material.NETHERITE_SPEAR))).isTrue();
  }

  @Test
  void spearsCountAsMeleeWeaponsLikeMaces() {
    assertThat(component.isMelee(item(Material.IRON_SPEAR))).isTrue();
    assertThat(component.isMelee(item(Material.DIAMOND_SPEAR))).isTrue();
  }
}
