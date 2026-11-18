package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.recipe.ModRecipeHost;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerInjectMixin {
    @Shadow private RecipeMap recipes;

    @Inject(method = "finalizeRecipeLoading", at = @At("HEAD"))
    private void adapt$injectRecipes(FeatureFlagSet enabledFlags, CallbackInfo callback) {
        recipes = ModRecipeHost.inject(recipes);
    }
}
