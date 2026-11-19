package art.arcane.adapt.mod.feature.mixin;

import art.arcane.adapt.mod.feature.recipe.ModRecipeHost;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Merges Adapt recipes once a (re)load has bound the new item tags, which recipe validation reads. */
@Mixin(ReloadableServerResources.class)
public abstract class RecipeManagerInjectMixin {
    @Inject(method = "updateRegistryTags()V", at = @At("TAIL"))
    private void adapt$injectRecipes(CallbackInfo callback) {
        ModRecipeHost.inject(((ReloadableServerResources) (Object) this).getRecipeManager());
    }
}
