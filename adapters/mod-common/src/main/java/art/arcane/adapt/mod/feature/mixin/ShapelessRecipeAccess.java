package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeAccess {
    @Accessor("result")
    ItemStackTemplate adapt$result();

    @Accessor("ingredients")
    List<Ingredient> adapt$ingredients();
}
