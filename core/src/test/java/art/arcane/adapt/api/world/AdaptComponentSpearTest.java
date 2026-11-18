package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptComponentSpearTest {
  private final AdaptComponent component = new AdaptComponent() {
  };

  private ItemRef item(Id material) {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(material);
    return stack;
  }

  @Test
  void everySpearTierIsRecognized() {
    assertThat(component.isSpear(item(Id.minecraft("wooden_spear")))).isTrue();
    assertThat(component.isSpear(item(Id.minecraft("stone_spear")))).isTrue();
    assertThat(component.isSpear(item(Id.minecraft("copper_spear")))).isTrue();
    assertThat(component.isSpear(item(Id.minecraft("iron_spear")))).isTrue();
    assertThat(component.isSpear(item(Id.minecraft("golden_spear")))).isTrue();
    assertThat(component.isSpear(item(Id.minecraft("diamond_spear")))).isTrue();
    assertThat(component.isSpear(item(Id.minecraft("netherite_spear")))).isTrue();
  }

  @Test
  void nonSpearItemsAreRejected() {
    assertThat(component.isSpear(item(Id.minecraft("trident")))).isFalse();
    assertThat(component.isSpear(item(Id.minecraft("mace")))).isFalse();
    assertThat(component.isSpear(item(Id.minecraft("air")))).isFalse();
    assertThat(component.isSpear(null)).isFalse();
  }

  @Test
  void spearsCountAsToolsLikeMaces() {
    assertThat(component.isTool(item(Id.minecraft("iron_spear")))).isTrue();
    assertThat(component.isTool(item(Id.minecraft("wooden_spear")))).isTrue();
    assertThat(component.isTool(item(Id.minecraft("netherite_spear")))).isTrue();
  }

  @Test
  void spearsCountAsMeleeWeaponsLikeMaces() {
    assertThat(component.isMelee(item(Id.minecraft("iron_spear")))).isTrue();
    assertThat(component.isMelee(item(Id.minecraft("diamond_spear")))).isTrue();
  }
}
