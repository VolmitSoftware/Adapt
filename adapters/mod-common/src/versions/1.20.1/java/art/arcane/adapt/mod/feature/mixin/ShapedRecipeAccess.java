package art.arcane.adapt.mod.feature.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ShapedRecipe.class)
public interface ShapedRecipeAccess {
    @Accessor("result")
    ItemStack adapt$result();

    @Invoker("shrink")
    static String[] adapt$shrink(String... rows) {
        throw new AssertionError("Mixin invoker was not applied");
    }

    @Invoker("dissolvePattern")
    static NonNullList<Ingredient> adapt$dissolvePattern(String[] rows, Map<String, Ingredient> key, int width, int height) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
