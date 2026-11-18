package art.arcane.adapt.mod.feature.recipe;

import art.arcane.adapt.api.value.MaterialCount;
import art.arcane.adapt.api.value.MaterialRecipe;
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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.level.block.Block;

public final class ModMaterialValuePort implements MaterialValuePort {
    private static final String ADAPT_NAMESPACE = "adapt";

    @Override
    public List<MaterialRecipe> recipesFor(Id material) {
        MinecraftServer server = ModPlatform.server();
        if (server == null || material == null) {
            return List.of();
        }
        Optional<Holder.Reference<Item>> wanted = BuiltInRegistries.ITEM.get(ModConversions.identifier(material));
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<MaterialRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : ModRecipeHost.ordered(server.getRecipeManager())) {
            if (ADAPT_NAMESPACE.equals(holder.id().identifier().getNamespace())) {
                continue;
            }
            MaterialRecipe converted = convert(holder.value(), wanted.get().value());
            if (converted != null) {
                recipes.add(converted);
            }
        }
        return recipes;
    }

    @Override
    public boolean breaksInstantly(Id material) {
        if (material == null) {
            return false;
        }
        Optional<Holder.Reference<Block>> block = BuiltInRegistries.BLOCK.get(ModConversions.identifier(material));
        return block.isPresent() && block.get().value().defaultDestroyTime() == 0F;
    }

    private static MaterialRecipe convert(Recipe<?> recipe, Item wanted) {
        ItemStackTemplate result = result(recipe);
        if (result == null || result.item().value() != wanted) {
            return null;
        }
        List<Ingredient> ingredients = ingredients(recipe);
        Map<Id, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            Optional<Id> first = ingredient.items()
                .map((Holder<Item> item) -> item.unwrapKey())
                .flatMap(Optional::stream)
                .map((ResourceKey<Item> key) -> ModRefs.id(key.identifier()))
                .findFirst();
            first.ifPresent((Id id) -> counts.merge(id, 1, Integer::sum));
        }
        if (counts.isEmpty()) {
            return null;
        }
        List<MaterialCount> input = new ArrayList<>(counts.size());
        counts.forEach((Id id, Integer amount) -> input.add(new MaterialCount(id, amount)));
        return new MaterialRecipe(input, new MaterialCount(ModRefs.id(BuiltInRegistries.ITEM.getKey(wanted)), result.count()));
    }

    private static ItemStackTemplate result(Recipe<?> recipe) {
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
        if (recipe instanceof ShapedRecipe shaped) {
            List<Ingredient> cells = new ArrayList<>();
            shaped.getIngredients().forEach((Optional<Ingredient> cell) -> cell.ifPresent(cells::add));
            return cells;
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return ((ShapelessRecipeAccess) shapeless).adapt$ingredients();
        }
        if (recipe instanceof SingleItemRecipe single) {
            return List.of(single.input());
        }
        return List.of();
    }
}
