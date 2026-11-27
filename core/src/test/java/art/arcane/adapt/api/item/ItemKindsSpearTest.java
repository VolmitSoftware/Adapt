package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemKindsSpearTest {
  private ItemRef item(Id material) {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(material);
    return stack;
  }

  @Test
  void everySpearTierIsRecognized() {
    assertThat(ItemKinds.isSpear(item(Id.minecraft("wooden_spear")))).isTrue();
    assertThat(ItemKinds.isSpear(item(Id.minecraft("stone_spear")))).isTrue();
    assertThat(ItemKinds.isSpear(item(Id.minecraft("copper_spear")))).isTrue();
    assertThat(ItemKinds.isSpear(item(Id.minecraft("iron_spear")))).isTrue();
    assertThat(ItemKinds.isSpear(item(Id.minecraft("golden_spear")))).isTrue();
    assertThat(ItemKinds.isSpear(item(Id.minecraft("diamond_spear")))).isTrue();
    assertThat(ItemKinds.isSpear(item(Id.minecraft("netherite_spear")))).isTrue();
  }

  @Test
  void nonSpearItemsAreRejected() {
    assertThat(ItemKinds.isSpear(item(Id.minecraft("trident")))).isFalse();
    assertThat(ItemKinds.isSpear(item(Id.minecraft("mace")))).isFalse();
    assertThat(ItemKinds.isSpear(item(Id.minecraft("air")))).isFalse();
    assertThat(ItemKinds.isSpear(null)).isFalse();
  }

  @Test
  void spearsCountAsToolsLikeMaces() {
    assertThat(ItemKinds.isTool(item(Id.minecraft("iron_spear")))).isTrue();
    assertThat(ItemKinds.isTool(item(Id.minecraft("wooden_spear")))).isTrue();
    assertThat(ItemKinds.isTool(item(Id.minecraft("netherite_spear")))).isTrue();
  }

  @Test
  void spearsCountAsMeleeWeaponsLikeMaces() {
    assertThat(ItemKinds.isMelee(item(Id.minecraft("iron_spear")))).isTrue();
    assertThat(ItemKinds.isMelee(item(Id.minecraft("diamond_spear")))).isTrue();
  }
}
