package art.arcane.adapt.mod.feature.recipe;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
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
        MappedRegistry<Recipe<?>> registry = new MappedRegistry<>(Registries.RECIPE, Lifecycle.stable());
        for (RecipeHolder<?> holder : recipes) {
            registry.register(holder.id(), holder.value(), RegistrationInfo.BUILT_IN);
        }
        registry.freeze();
        return RecipeMap.create(registry);
    }
}
