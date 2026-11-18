package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptComponentCopperTierTest {
  private final AdaptComponent component = new AdaptComponent() {
  };

  private ItemRef item(Id material) {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(material);
    return stack;
  }

  @Test
  void copperToolsAreRecognizedByToolTypeChecks() {
    assertThat(component.isSword(item(Id.minecraft("copper_sword")))).isTrue();
    assertThat(component.isAxe(item(Id.minecraft("copper_axe")))).isTrue();
    assertThat(component.isPickaxe(item(Id.minecraft("copper_pickaxe")))).isTrue();
    assertThat(component.isShovel(item(Id.minecraft("copper_shovel")))).isTrue();
    assertThat(component.isHoe(item(Id.minecraft("copper_hoe")))).isTrue();
    assertThat(component.isTool(item(Id.minecraft("copper_sword")))).isTrue();
    assertThat(component.isMelee(item(Id.minecraft("copper_sword")))).isTrue();
  }

  @Test
  void copperArmorIsRecognizedByArmorTypeChecks() {
    assertThat(component.isHelmet(item(Id.minecraft("copper_helmet")))).isTrue();
    assertThat(component.isChestplate(item(Id.minecraft("copper_chestplate")))).isTrue();
    assertThat(component.isLeggings(item(Id.minecraft("copper_leggings")))).isTrue();
    assertThat(component.isBoots(item(Id.minecraft("copper_boots")))).isTrue();
  }

  @Test
  void nonToolMaterialsAreStillRejected() {
    ItemRef ingot = item(Id.minecraft("copper_ingot"));
    assertThat(component.isSword(ingot)).isFalse();
    assertThat(component.isAxe(ingot)).isFalse();
    assertThat(component.isPickaxe(ingot)).isFalse();
    assertThat(component.isShovel(ingot)).isFalse();
    assertThat(component.isHoe(ingot)).isFalse();
  }
}
