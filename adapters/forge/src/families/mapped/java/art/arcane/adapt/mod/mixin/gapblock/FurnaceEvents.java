package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Furnaces, smokers and blast furnaces finishing one item. Forge's {@code burn} is an instance method that assembles
 * the result itself, so a replaced result is handed to that assembly and a cancelled smelt skips {@code burn}, which
 * also skips crediting the recipe.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceEvents {
    @Unique
    private static ItemStack adapt$replacement;

    @WrapOperation(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/core/NonNullList;I)Z"))
    private static boolean adapt$smelting(AbstractFurnaceBlockEntity furnace, RegistryAccess registries, RecipeHolder<?> recipe,
                                          NonNullList<ItemStack> items, int maxStackSize, Operation<Boolean> original,
                                          @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        if (recipe == null || !(level instanceof ServerLevel serverLevel)) {
            return original.call(furnace, registries, recipe, items, maxStackSize);
        }
        ItemStack ingredient = items.get(0);
        @SuppressWarnings("unchecked")
        Recipe<SingleRecipeInput> smelting = (Recipe<SingleRecipeInput>) recipe.value();
        ItemStack result = smelting.assemble(new SingleRecipeInput(ingredient), registries);
        ItemStack chosen = BlockHooks.smelt(serverLevel, pos, items, ingredient, result);
        if (chosen == null) {
            BlockHooks.consumeSmeltCancel();
            return false;
        }
        adapt$replacement = chosen == result ? null : chosen;
        try {
            return original.call(furnace, registries, recipe, items, maxStackSize);
        } finally {
            adapt$replacement = null;
        }
    }

    @ModifyExpressionValue(method = "burn(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/core/NonNullList;I)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/Recipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack adapt$smelted(ItemStack assembled) {
        return adapt$replacement == null ? assembled : adapt$replacement;
    }
}
