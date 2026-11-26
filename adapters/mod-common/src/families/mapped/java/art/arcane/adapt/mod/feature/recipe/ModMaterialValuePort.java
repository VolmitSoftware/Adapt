package art.arcane.adapt.mod.feature.recipe;

import art.arcane.adapt.api.value.MaterialCount;
import art.arcane.adapt.api.value.MaterialRecipe;
import art.arcane.adapt.api.value.MaterialRecipeIndex;
import art.arcane.adapt.api.value.MaterialValuePort;
import art.arcane.adapt.mod.feature.mixin.ShapedRecipeAccess;
import art.arcane.adapt.mod.feature.mixin.ShapelessRecipeAccess;
import art.arcane.adapt.mod.feature.mixin.SingleItemRecipeAccess;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.value.Id;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.level.block.Block;

public final class ModMaterialValuePort implements MaterialValuePort {
    private static final String ADAPT_NAMESPACE = "adapt";

    private final MaterialRecipeIndex<Item, RecipeHolder<?>> index =
        new MaterialRecipeIndex<>(ModMaterialValuePort::output, ModMaterialValuePort::convert);

    @Override
    public List<MaterialRecipe> recipesFor(Id material) {
        MinecraftServer server = ModPlatform.server();
        if (server == null || material == null) {
            return List.of();
        }
        Optional<Item> wanted = BuiltInRegistries.ITEM.getOptional(ModConversions.identifier(material));
        if (wanted.isEmpty()) {
            return List.of();
        }
        RecipeManager manager = server.getRecipeManager();
        return index.recipesFor(manager.getOrderedRecipes(), () -> ModRecipeHost.ordered(manager), wanted.get());
    }

    @Override
    public boolean breaksInstantly(Id material) {
        if (material == null) {
            return false;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ModConversions.identifier(material));
        return block.isPresent() && block.get().defaultDestroyTime() == 0F;
    }

    private static Item output(RecipeHolder<?> holder) {
        if (ADAPT_NAMESPACE.equals(holder.id().getNamespace())) {
            return null;
        }
        ItemStack result = result(holder.value());
        return result == null ? null : result.getItem();
    }

    private static MaterialRecipe convert(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        ItemStack result = result(recipe);
        Item wanted = result.getItem();
        List<Ingredient> ingredients = ingredients(recipe);
        Map<Id, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0) {
                counts.merge(ModRefs.id(BuiltInRegistries.ITEM.getKey(items[0].getItem())), 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return null;
        }
        List<MaterialCount> input = new ArrayList<>(counts.size());
        counts.forEach((Id id, Integer amount) -> input.add(new MaterialCount(id, amount)));
        return new MaterialRecipe(input, new MaterialCount(ModRefs.id(BuiltInRegistries.ITEM.getKey(wanted)), result.getCount()),
            kind(recipe));
    }

    private static MaterialRecipe.Kind kind(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            return MaterialRecipe.Kind.CRAFTING;
        }
        return recipe instanceof SingleItemRecipe ? MaterialRecipe.Kind.STONECUTTING : MaterialRecipe.Kind.OTHER;
    }

    private static ItemStack result(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return ((ShapedRecipeAccess) shaped).adapt$result();
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return ((ShapelessRecipeAccess) shapeless).adapt$result();
        }
        if (recipe instanceof SingleItemRecipe single) {
            return ((SingleItemRecipeAccess) single).adapt$result();
        }
        return null;
    }

    private static List<Ingredient> ingredients(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe || recipe instanceof SingleItemRecipe) {
            List<Ingredient> cells = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (!ingredient.isEmpty()) {
                    cells.add(ingredient);
                }
            }
            return cells;
        }
        return List.of();
    }
}
