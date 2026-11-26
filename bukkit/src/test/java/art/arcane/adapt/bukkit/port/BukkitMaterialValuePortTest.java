package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.value.MaterialRecipe;
import art.arcane.adapt.bukkit.feature.recipe.BukkitRecipeRegistry;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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

  @Test
  void recipesComeFromOneScanUntilTheRegistryChanges() {
    List<Recipe> all = new ArrayList<>(List.of(cutting("plank", Material.OAK_PLANKS, Material.OAK_LOG),
        cutting("slab", Material.OAK_SLAB, Material.OAK_PLANKS), cutting("plank_two", Material.OAK_PLANKS, Material.OAK_WOOD)));
    StonecuttingRecipe adapt = cutting("plank_adapt", Material.OAK_PLANKS, Material.STONE);
    when(adapt.getKey()).thenReturn(new NamespacedKey("adapt", "plank_adapt"));
    all.add(adapt);
    AtomicInteger scans = new AtomicInteger();
    BukkitRecipeRegistry registry = new BukkitRecipeRegistry(() -> {
      scans.incrementAndGet();
      return List.copyOf(all).iterator();
    }, Event.class, () -> 0L);
    BukkitMaterialValuePort port = new BukkitMaterialValuePort(registry);

    try (MockedStatic<BukkitRefs> refs = mockStatic(BukkitRefs.class)) {
      refs.when(() -> BukkitRefs.material(any(Id.class))).thenAnswer(invocation -> Material.matchMaterial(invocation.<Id>getArgument(0).toString()));
      refs.when(() -> BukkitRefs.id(any(Material.class))).thenAnswer(invocation -> Id.minecraft(invocation.<Material>getArgument(0).name().toLowerCase(Locale.ROOT)));

      assertThat(port.recipesFor(Id.minecraft("oak_planks"))).extracting((MaterialRecipe recipe) -> recipe.getInput().getFirst().getMaterial())
          .containsExactly(Id.minecraft("oak_log"), Id.minecraft("oak_wood"));
      assertThat(port.recipesFor(Id.minecraft("oak_slab"))).hasSize(1);
      assertThat(port.recipesFor(Id.minecraft("oak_planks"))).hasSize(2);
      assertThat(port.recipesFor(Id.minecraft("dirt"))).isEmpty();
      assertThat(scans).hasValue(1);

      all.add(cutting("slab_two", Material.OAK_SLAB, Material.OAK_LOG));
      registry.invalidate();
      assertThat(port.recipesFor(Id.minecraft("oak_slab"))).hasSize(2);
      assertThat(scans).hasValue(2);
    }
  }

  private static StonecuttingRecipe cutting(String key, Material output, Material input) {
    StonecuttingRecipe recipe = mock(StonecuttingRecipe.class);
    when(recipe.getKey()).thenReturn(NamespacedKey.minecraft(key));
    ItemStack result = mock(ItemStack.class);
    when(result.getType()).thenReturn(output);
    when(result.getAmount()).thenReturn(1);
    ItemStack source = mock(ItemStack.class);
    when(source.getType()).thenReturn(input);
    when(recipe.getResult()).thenReturn(result);
    when(recipe.getInput()).thenReturn(source);
    return recipe;
  }
}
