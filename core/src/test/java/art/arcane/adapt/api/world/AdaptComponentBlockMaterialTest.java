package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptComponentBlockMaterialTest {
  private final AdaptComponent component = new AdaptComponent() {
  };

  @Test
  void logMaterialsAreRecognized() {
    assertThat(component.isLog(Id.minecraft("oak_log"))).isTrue();
    assertThat(component.isLog(Id.minecraft("stripped_spruce_log"))).isTrue();
    assertThat(component.isLog(Id.minecraft("dark_oak_wood"))).isTrue();
    assertThat(component.isLog(Id.minecraft("mushroom_stem"))).isTrue();
    assertThat(component.isLog(Id.minecraft("brown_mushroom_block"))).isTrue();
    assertThat(component.isLog(Id.minecraft("red_mushroom_block"))).isTrue();
    assertThat(component.isLog(Id.minecraft("mangrove_roots"))).isTrue();
    assertThat(component.isLog(Id.minecraft("muddy_mangrove_roots"))).isTrue();
  }

  @Test
  void leavesMaterialsAreRecognized() {
    assertThat(component.isLeaves(Id.minecraft("oak_leaves"))).isTrue();
    assertThat(component.isLeaves(Id.minecraft("azalea_leaves"))).isTrue();
    assertThat(component.isLeaves(Id.minecraft("mangrove_roots"))).isTrue();
    assertThat(component.isLeaves(Id.minecraft("muddy_mangrove_roots"))).isTrue();
  }

  @Test
  void nonItemBlockMaterialsAreRejectedWithoutThrowing() {
    assertThat(component.isLog(Id.minecraft("cocoa"))).isFalse();
    assertThat(component.isLeaves(Id.minecraft("cocoa"))).isFalse();
    assertThat(component.isLog(Id.minecraft("wall_torch"))).isFalse();
    assertThat(component.isLeaves(Id.minecraft("wall_torch"))).isFalse();
    assertThat(component.isLog(Id.minecraft("carrots"))).isFalse();
    assertThat(component.isLeaves(Id.minecraft("carrots"))).isFalse();
  }

  @Test
  void ordinaryMaterialsAreRejected() {
    assertThat(component.isLog(Id.minecraft("stone"))).isFalse();
    assertThat(component.isLeaves(Id.minecraft("stone"))).isFalse();
    assertThat(component.isLog(Id.minecraft("air"))).isFalse();
    assertThat(component.isLeaves(Id.minecraft("air"))).isFalse();
  }
}
