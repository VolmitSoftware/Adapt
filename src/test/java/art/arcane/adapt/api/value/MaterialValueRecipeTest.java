package art.arcane.adapt.api.value;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MaterialValueRecipeTest extends AdaptTestBase {
    private final Map<Material, List<Recipe>> recipes = new HashMap<>();
    private MockedStatic<AdaptConfig> config;
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ItemStack> items;
    private Material ingot;
    private Material chestplate;
    private Material nugget;

    @BeforeEach
    void configureRecipes() {
        AdaptConfig settings = new AdaptConfig();
        settings.getValue().getValueMultipliers().clear();
        config = mockStatic(AdaptConfig.class);
        config.when(AdaptConfig::get).thenReturn(settings);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getRecipesFor(any(ItemStack.class)))
                .thenAnswer(invocation -> recipes.getOrDefault(invocation.<ItemStack>getArgument(0).getType(), List.of()));
        items = mockConstruction(ItemStack.class, (item, construction) -> {
            when(item.getType()).thenReturn((Material) construction.arguments().getFirst());
            when(item.getAmount()).thenReturn(construction.arguments().size() > 1 ? (int) construction.arguments().get(1) : 1);
        });
        ingot = material("IRON_INGOT");
        chestplate = material("IRON_CHESTPLATE");
        nugget = material("IRON_NUGGET");
        MaterialValue.invalidateCache();
    }

    @AfterEach
    void cleanup() {
        MaterialValue.invalidateCache();
        if (items != null) {
            items.close();
        }
        if (bukkit != null) {
            bukkit.close();
        }
        if (config != null) {
            config.close();
        }
    }

    @Test
    void chestplateIncludesEightOccupiedIngredientSlots() {
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        ItemStack result = new ItemStack(chestplate);
        ItemStack ingredient = new ItemStack(ingot);
        when(recipe.getResult()).thenReturn(result);
        when(recipe.getShape()).thenReturn(new String[]{"I I", "III", "III"});
        when(recipe.getIngredientMap()).thenReturn(Map.of('I', ingredient));
        recipes.put(chestplate, List.of(recipe));

        assertThat(MaterialValue.getValue(chestplate)).isEqualTo(10);
        assertThat(MaterialValue.getValue(chestplate)).isGreaterThan(4 * MaterialValue.getValue(ingot));
    }

    @Test
    void repeatedSymbolsForTheSameMaterialRespectOutputStackSize() {
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        ItemStack result = new ItemStack(nugget, 4);
        ItemStack ingredient = new ItemStack(ingot);
        when(recipe.getResult()).thenReturn(result);
        when(recipe.getShape()).thenReturn(new String[]{"AB", " A"});
        when(recipe.getIngredientMap()).thenReturn(Map.of('A', ingredient, 'B', ingredient));
        recipes.put(nugget, List.of(recipe));

        assertThat(MaterialValue.getValue(nugget)).isEqualTo(2);
    }

    private Material material(String name) {
        Material material = mock(Material.class);
        when(material.name()).thenReturn(name);
        return material;
    }
}
