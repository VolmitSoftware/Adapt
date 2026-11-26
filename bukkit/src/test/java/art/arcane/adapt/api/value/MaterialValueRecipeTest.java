package art.arcane.adapt.api.value;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.port.BukkitTextPorts;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MaterialValueRecipeTest extends AdaptTestBase {
    private final Map<Material, List<Recipe>> recipes = new HashMap<>();
    private MockedStatic<AdaptConfig> config;
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ItemStack> items;
    private Id ingot;
    private Id chestplate;
    private Id nugget;
    private Material ingotIngredient;

    @BeforeEach
    void configureRecipes() {
        AdaptConfig settings = new AdaptConfig();
        settings.getValue().getValueMultipliers().clear();
        config = mockStatic(AdaptConfig.class);
        config.when(AdaptConfig::get).thenReturn(settings);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::recipeIterator)
                .thenAnswer(invocation -> recipes.values().stream().flatMap(List::stream).toList().iterator());
        items = mockConstruction(ItemStack.class, (item, construction) -> {
            when(item.getType()).thenReturn((Material) construction.arguments().getFirst());
            when(item.getAmount()).thenReturn(construction.arguments().size() > 1 ? (int) construction.arguments().get(1) : 1);
        });
        BukkitTextPorts.install(plugin);
        MaterialValuePort bukkitPort = AdaptPorts.get(MaterialValuePort.class, MaterialValuePort.NONE);
        AdaptPorts.install(MaterialValuePort.class, new MaterialValuePort() {
            @Override
            public List<MaterialRecipe> recipesFor(Id material) {
                return bukkitPort.recipesFor(material);
            }

            @Override
            public boolean breaksInstantly(Id material) {
                return false;
            }
        });
        ingot = BukkitRefs.id(Material.IRON_INGOT);
        chestplate = BukkitRefs.id(Material.IRON_CHESTPLATE);
        nugget = BukkitRefs.id(Material.IRON_NUGGET);
        ingotIngredient = material("IRON_INGOT");
        MaterialValue.invalidateCache();
    }

    @AfterEach
    void cleanup() {
        MaterialValue.invalidateCache();
        BukkitTextPorts.uninstall();
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
    void adaptRecipesAreSkippedByTheirNamespaceNotTheirClass() {
        ShapedRecipe adapt = mock(ShapedRecipe.class);
        when(adapt.getKey()).thenReturn(new NamespacedKey("adapt", "golemancy-blank-core"));
        ItemStack adaptResult = new ItemStack(Material.IRON_CHESTPLATE);
        ItemStack ingredient = new ItemStack(ingotIngredient);
        when(adapt.getResult()).thenReturn(adaptResult);
        when(adapt.getShape()).thenReturn(new String[]{"I I", "III", "III"});
        when(adapt.getIngredientMap()).thenReturn(Map.of('I', ingredient));
        ShapedRecipe vanilla = mock(ShapedRecipe.class);
        when(vanilla.getKey()).thenReturn(NamespacedKey.minecraft("iron_nugget"));
        ItemStack vanillaResult = new ItemStack(Material.IRON_NUGGET);
        when(vanilla.getResult()).thenReturn(vanillaResult);
        when(vanilla.getShape()).thenReturn(new String[]{"I"});
        when(vanilla.getIngredientMap()).thenReturn(Map.of('I', ingredient));
        recipes.put(Material.IRON_CHESTPLATE, List.of(adapt));
        recipes.put(Material.IRON_NUGGET, List.of(vanilla));

        assertThat(MaterialValue.getValue(chestplate)).isEqualTo(1);
        assertThat(MaterialValue.getValue(nugget)).isEqualTo(3);
    }

    @Test
    void chestplateIncludesEightOccupiedIngredientSlots() {
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        ItemStack result = new ItemStack(Material.IRON_CHESTPLATE);
        ItemStack ingredient = new ItemStack(ingotIngredient);
        when(recipe.getResult()).thenReturn(result);
        when(recipe.getShape()).thenReturn(new String[]{"I I", "III", "III"});
        when(recipe.getIngredientMap()).thenReturn(Map.of('I', ingredient));
        recipes.put(Material.IRON_CHESTPLATE, List.of(recipe));

        assertThat(MaterialValue.getValue(chestplate)).isEqualTo(10);
        assertThat(MaterialValue.getValue(chestplate)).isGreaterThan(4 * MaterialValue.getValue(ingot));
    }

    @Test
    void repeatedSymbolsForTheSameMaterialRespectOutputStackSize() {
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        ItemStack result = new ItemStack(Material.IRON_NUGGET, 4);
        ItemStack ingredient = new ItemStack(ingotIngredient);
        when(recipe.getResult()).thenReturn(result);
        when(recipe.getShape()).thenReturn(new String[]{"AB", " A"});
        when(recipe.getIngredientMap()).thenReturn(Map.of('A', ingredient, 'B', ingredient));
        recipes.put(Material.IRON_NUGGET, List.of(recipe));

        assertThat(MaterialValue.getValue(nugget)).isEqualTo(2);
    }

    private Material material(String name) {
        Material material = mock(Material.class);
        when(material.name()).thenReturn(name);
        when(material.getKey()).thenReturn(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return material;
    }
}
