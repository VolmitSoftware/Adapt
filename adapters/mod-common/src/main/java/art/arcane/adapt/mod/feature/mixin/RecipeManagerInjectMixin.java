package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.recipe.ModRecipeHost;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerInjectMixin {
    @Shadow @Final @Mutable private RecipeMap recipes;
    @Shadow @Final @Mutable private Collection<RecipeHolder<?>> learnableRecipes;

    @Inject(method = "finalizeRecipeLoading", at = @At("HEAD"))
    private void adapt$injectRecipes(FeatureFlagSet enabledFlags, CallbackInfo callback) {
        RecipeMap merged = ModRecipeHost.inject(recipes);
        if (merged != recipes) {
            recipes = merged;
            learnableRecipes = ModRecipeHost.learnable(merged);
        }
    }
}
