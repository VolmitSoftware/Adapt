package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemKindsBlockMaterialTest {
  @Test
  void logMaterialsAreRecognized() {
    assertThat(ItemKinds.isLog(Id.minecraft("oak_log"))).isTrue();
    assertThat(ItemKinds.isLog(Id.minecraft("stripped_spruce_log"))).isTrue();
    assertThat(ItemKinds.isLog(Id.minecraft("dark_oak_wood"))).isTrue();
    assertThat(ItemKinds.isLog(Id.minecraft("mushroom_stem"))).isTrue();
    assertThat(ItemKinds.isLog(Id.minecraft("brown_mushroom_block"))).isTrue();
    assertThat(ItemKinds.isLog(Id.minecraft("red_mushroom_block"))).isTrue();
    assertThat(ItemKinds.isLog(Id.minecraft("mangrove_roots"))).isTrue();
    assertThat(ItemKinds.isLog(Id.minecraft("muddy_mangrove_roots"))).isTrue();
  }

  @Test
  void leavesMaterialsAreRecognized() {
    assertThat(ItemKinds.isLeaves(Id.minecraft("oak_leaves"))).isTrue();
    assertThat(ItemKinds.isLeaves(Id.minecraft("azalea_leaves"))).isTrue();
    assertThat(ItemKinds.isLeaves(Id.minecraft("mangrove_roots"))).isTrue();
    assertThat(ItemKinds.isLeaves(Id.minecraft("muddy_mangrove_roots"))).isTrue();
  }

  @Test
  void nonItemBlockMaterialsAreRejectedWithoutThrowing() {
    assertThat(ItemKinds.isLog(Id.minecraft("cocoa"))).isFalse();
    assertThat(ItemKinds.isLeaves(Id.minecraft("cocoa"))).isFalse();
    assertThat(ItemKinds.isLog(Id.minecraft("wall_torch"))).isFalse();
    assertThat(ItemKinds.isLeaves(Id.minecraft("wall_torch"))).isFalse();
    assertThat(ItemKinds.isLog(Id.minecraft("carrots"))).isFalse();
    assertThat(ItemKinds.isLeaves(Id.minecraft("carrots"))).isFalse();
  }

  @Test
  void ordinaryMaterialsAreRejected() {
    assertThat(ItemKinds.isLog(Id.minecraft("stone"))).isFalse();
    assertThat(ItemKinds.isLeaves(Id.minecraft("stone"))).isFalse();
    assertThat(ItemKinds.isLog(Id.minecraft("air"))).isFalse();
    assertThat(ItemKinds.isLeaves(Id.minecraft("air"))).isFalse();
  }
}
