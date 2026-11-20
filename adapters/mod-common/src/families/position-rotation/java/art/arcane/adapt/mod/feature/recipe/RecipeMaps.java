package art.arcane.adapt.mod.feature.recipe;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

import java.util.List;

/**
 * Builds a recipe map through {@link RecipeMap#create}, the factory the loaders hook (Fabric API indexes the recipes it
 * synchronizes to clients there), so a merged map behaves like one the server loaded itself.
 */
final class RecipeMaps {
    private RecipeMaps() {
    }

    static RecipeMap create(List<RecipeHolder<?>> recipes) {
        return RecipeMap.create(recipes);
    }
}
