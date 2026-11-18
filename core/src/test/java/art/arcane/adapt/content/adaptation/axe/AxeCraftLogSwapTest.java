package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AxeCraftLogSwapTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void registersEveryLogSwapInTheOriginalOrder() {
    List<RecipeSpec> recipes = new AxeCraftLogSwap().getRecipes();
    List<String> keys = new ArrayList<>();
    for (RecipeSpec recipe : recipes) {
      keys.add(recipe.key().toString());
    }

    assertThat(recipes).hasSize(70);
    assertThat(keys).startsWith("adapt:axe-swapbirchoak", "adapt:axe-swapbirchacacia", "adapt:axe-swapbirchdarkoak");
    assertThat(keys).endsWith("adapt:axe-swappaleoakjungle", "adapt:axe-swappaleoakspruce", "adapt:axe-swappaleoakcherry");
    assertThat(keys).contains("adapt:axe-swapmangrovecherry", "adapt:axe-swapcherrypaleoak", "adapt:axe-swapoakmangrove")
        .doesNotContain("adapt:axe-swapcherrymangrove", "adapt:axe-swappaleoakmangrove");
    assertThat(keys).doesNotHaveDuplicates();
  }

  @Test
  void eachSwapTakesEightSourceLogsAndTheTargetSaplingForEightTargetLogs() {
    RecipeSpec.Shapeless swap = null;
    for (RecipeSpec recipe : new AxeCraftLogSwap().getRecipes()) {
      if (recipe.key().equals(Id.adapt("axe-swapspruceacacia"))) {
        swap = (RecipeSpec.Shapeless) recipe;
      }
    }

    assertThat(swap).isNotNull();
    assertThat(swap.ingredients()).hasSize(9);
    assertThat(swap.ingredients().subList(0, 8)).containsOnly(Ingredient.of(Id.minecraft("spruce_log")));
    assertThat(swap.ingredients().get(8)).isEqualTo(Ingredient.of(Id.minecraft("acacia_sapling")));
    assertThat(swap.result().type()).isEqualTo(Id.minecraft("acacia_log"));
    assertThat(swap.result().amount()).isEqualTo(8);
  }

  @Test
  void woodsMissingFromTheRegistryAreSkipped() {
    RegistryLookup registries = mock(RegistryLookup.class);
    when(registries.exists(eq(RegistryKind.ITEM), any(Id.class))).thenReturn(true);
    when(registries.exists(RegistryKind.ITEM, Id.minecraft("pale_oak_sapling"))).thenReturn(false);

    List<String> woods = new ArrayList<>();
    for (AxeCraftLogSwap.Wood wood : AxeCraftLogSwap.availableWoods(registries)) {
      woods.add(wood.key());
    }

    assertThat(woods).containsExactly("birch", "oak", "acacia", "darkoak", "jungle", "spruce", "mangrove", "cherry");
  }
}
