package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Furnaces, smokers and blast furnaces finishing one item. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceEvents {
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void adapt$smelting(NonNullList<ItemStack> items, ItemStack ingredient, ItemStack result, Operation<Void> original,
                                       @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        ItemStack chosen = BlockHooks.smelt(level, pos, items, ingredient, result);
        if (chosen != null) {
            original.call(items, ingredient, chosen);
        }
    }

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;setRecipeUsed(Lnet/minecraft/world/item/crafting/RecipeHolder;)V"))
    private static void adapt$crediting(AbstractFurnaceBlockEntity furnace, RecipeHolder<?> recipe, Operation<Void> original) {
        if (!BlockHooks.consumeSmeltCancel()) {
            original.call(furnace, recipe);
        }
    }
}
