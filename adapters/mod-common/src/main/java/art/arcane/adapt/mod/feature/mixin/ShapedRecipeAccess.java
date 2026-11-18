package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShapedRecipe.class)
public interface ShapedRecipeAccess {
    @Accessor("result")
    ItemStackTemplate adapt$result();
}
