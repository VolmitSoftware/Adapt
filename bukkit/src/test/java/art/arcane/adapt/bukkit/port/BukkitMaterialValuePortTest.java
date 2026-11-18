package art.arcane.adapt.bukkit.port;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitMaterialValuePortTest {
  @Test
  void adaptRecipesAreRecognizedByTheirNamespaceNotTheirClass() {
    ShapedRecipe adapt = mock(ShapedRecipe.class);
    when(adapt.getKey()).thenReturn(new NamespacedKey("adapt", "golemancy-blank-core"));
    ShapedRecipe vanilla = mock(ShapedRecipe.class);
    when(vanilla.getKey()).thenReturn(NamespacedKey.minecraft("iron_block"));

    assertThat(BukkitMaterialValuePort.isAdaptRecipe(adapt)).isTrue();
    assertThat(BukkitMaterialValuePort.isAdaptRecipe(vanilla)).isFalse();
    assertThat(BukkitMaterialValuePort.isAdaptRecipe(mock(Recipe.class))).isFalse();
  }
}
