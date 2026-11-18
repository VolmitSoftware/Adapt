package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SingleItemRecipe.class)
public interface SingleItemRecipeAccess {
    @Accessor("result")
    ItemStackTemplate adapt$result();
}
