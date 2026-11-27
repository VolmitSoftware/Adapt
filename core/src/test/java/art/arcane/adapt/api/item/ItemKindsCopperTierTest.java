package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemKindsCopperTierTest {
  private ItemRef item(Id material) {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(material);
    return stack;
  }

  @Test
  void copperToolsAreRecognizedByToolTypeChecks() {
    assertThat(ItemKinds.isSword(item(Id.minecraft("copper_sword")))).isTrue();
    assertThat(ItemKinds.isAxe(item(Id.minecraft("copper_axe")))).isTrue();
    assertThat(ItemKinds.isPickaxe(item(Id.minecraft("copper_pickaxe")))).isTrue();
    assertThat(ItemKinds.isShovel(item(Id.minecraft("copper_shovel")))).isTrue();
    assertThat(ItemKinds.isHoe(item(Id.minecraft("copper_hoe")))).isTrue();
    assertThat(ItemKinds.isTool(item(Id.minecraft("copper_sword")))).isTrue();
    assertThat(ItemKinds.isMelee(item(Id.minecraft("copper_sword")))).isTrue();
  }

  @Test
  void copperArmorIsRecognizedByArmorTypeChecks() {
    assertThat(ItemKinds.isHelmet(item(Id.minecraft("copper_helmet")))).isTrue();
    assertThat(ItemKinds.isChestplate(item(Id.minecraft("copper_chestplate")))).isTrue();
    assertThat(ItemKinds.isLeggings(item(Id.minecraft("copper_leggings")))).isTrue();
    assertThat(ItemKinds.isBoots(item(Id.minecraft("copper_boots")))).isTrue();
  }

  @Test
  void nonToolMaterialsAreStillRejected() {
    ItemRef ingot = item(Id.minecraft("copper_ingot"));
    assertThat(ItemKinds.isSword(ingot)).isFalse();
    assertThat(ItemKinds.isAxe(ingot)).isFalse();
    assertThat(ItemKinds.isPickaxe(ingot)).isFalse();
    assertThat(ItemKinds.isShovel(ingot)).isFalse();
    assertThat(ItemKinds.isHoe(ingot)).isFalse();
  }
}
