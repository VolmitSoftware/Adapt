package art.arcane.adapt.mod.feature.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RecipeMapsTest {
    private static final Recipe.CommonInfo COMMON = new Recipe.CommonInfo(true);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void create_keepsEveryRecipeByKeyAndType() {
        RecipeHolder<?> crafted = holder("adapt:crafted", new ShapelessRecipe(COMMON,
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""), new ItemStackTemplate(Items.DIAMOND),
                List.of(Ingredient.of(Items.STICK))));
        RecipeHolder<?> smelted = holder("minecraft:smelted", new SmeltingRecipe(COMMON,
                new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, ""), Ingredient.of(Items.COBBLESTONE),
                new ItemStackTemplate(Items.STONE), 0.1F, 200));

        RecipeMap map = RecipeMaps.create(List.of(crafted, smelted));

        assertEquals(2, map.values().size());
        assertSame(crafted.value(), map.byKey(crafted.id()).value());
        assertSame(smelted.value(), map.byKey(smelted.id()).value());
        assertEquals(List.of(crafted.id()), map.byType(RecipeType.CRAFTING).stream().map(RecipeHolder::id).toList());
        assertEquals(List.of(smelted.id()), map.byType(RecipeType.SMELTING).stream().map(RecipeHolder::id).toList());
    }

    @Test
    void create_withNoRecipes_isEmpty() {
        assertEquals(0, RecipeMaps.create(List.of()).values().size());
    }

    private static RecipeHolder<?> holder(String id, Recipe<?> recipe) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)), recipe);
    }
}
