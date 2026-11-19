package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.platform.value.Id;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Before 1.21.5 both thrown potions are entity type minecraft:potion; core expects the 26.x split ids. */
class BukkitEntityRefPotionTypeTest {
  @SuppressWarnings("deprecation")
  private static final EntityType POTION = EntityType.fromName("potion");

  @Test
  void thrownPotionsReadAsSplashOrLingeringByTheirItem() {
    assertThat(new BukkitEntityRef(thrown(Material.SPLASH_POTION)).type()).isEqualTo(Id.minecraft("splash_potion"));
    assertThat(new BukkitEntityRef(thrown(Material.LINGERING_POTION)).type()).isEqualTo(Id.minecraft("lingering_potion"));
  }

  private static ThrownPotion thrown(Material item) {
    ThrownPotion potion = mock(ThrownPotion.class);
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(item);
    when(potion.getUniqueId()).thenReturn(UUID.randomUUID());
    when(potion.getType()).thenReturn(POTION);
    when(potion.getItem()).thenReturn(stack);
    return potion;
  }
}
