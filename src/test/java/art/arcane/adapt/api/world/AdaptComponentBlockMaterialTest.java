package art.arcane.adapt.api.world;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptComponentBlockMaterialTest {
  private final AdaptComponent component = new AdaptComponent() {
  };

  @Test
  void logMaterialsAreRecognized() {
    assertThat(component.isLog(Material.OAK_LOG)).isTrue();
    assertThat(component.isLog(Material.STRIPPED_SPRUCE_LOG)).isTrue();
    assertThat(component.isLog(Material.DARK_OAK_WOOD)).isTrue();
    assertThat(component.isLog(Material.MUSHROOM_STEM)).isTrue();
    assertThat(component.isLog(Material.BROWN_MUSHROOM_BLOCK)).isTrue();
    assertThat(component.isLog(Material.RED_MUSHROOM_BLOCK)).isTrue();
    assertThat(component.isLog(Material.MANGROVE_ROOTS)).isTrue();
    assertThat(component.isLog(Material.MUDDY_MANGROVE_ROOTS)).isTrue();
  }

  @Test
  void leavesMaterialsAreRecognized() {
    assertThat(component.isLeaves(Material.OAK_LEAVES)).isTrue();
    assertThat(component.isLeaves(Material.AZALEA_LEAVES)).isTrue();
    assertThat(component.isLeaves(Material.MANGROVE_ROOTS)).isTrue();
    assertThat(component.isLeaves(Material.MUDDY_MANGROVE_ROOTS)).isTrue();
  }

  @Test
  void nonItemBlockMaterialsAreRejectedWithoutThrowing() {
    assertThat(component.isLog(Material.COCOA)).isFalse();
    assertThat(component.isLeaves(Material.COCOA)).isFalse();
    assertThat(component.isLog(Material.WALL_TORCH)).isFalse();
    assertThat(component.isLeaves(Material.WALL_TORCH)).isFalse();
    assertThat(component.isLog(Material.CARROTS)).isFalse();
    assertThat(component.isLeaves(Material.CARROTS)).isFalse();
  }

  @Test
  void ordinaryMaterialsAreRejected() {
    assertThat(component.isLog(Material.STONE)).isFalse();
    assertThat(component.isLeaves(Material.STONE)).isFalse();
    assertThat(component.isLog(Material.AIR)).isFalse();
    assertThat(component.isLeaves(Material.AIR)).isFalse();
  }
}
