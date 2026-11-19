package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Furnaces, smokers and blast furnaces finishing one item. The smelt is decided inside burn, after the result is
 * assembled and before the output slot is read; a cancel makes burn report nothing burned, so the recipe is not credited.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceEvents {
    @Unique
    private static ServerLevel adapt$level;
    @Unique
    private static BlockPos adapt$pos;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void adapt$ticking(CallbackInfo callback, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        adapt$level = level instanceof ServerLevel serverLevel ? serverLevel : null;
        adapt$pos = pos;
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void adapt$ticked(CallbackInfo callback) {
        adapt$level = null;
        adapt$pos = null;
    }

    @Inject(method = "burn", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;", ordinal = 1))
    private static void adapt$smelting(CallbackInfoReturnable<Boolean> callback, @Local(argsOnly = true) NonNullList<ItemStack> items,
                                       @Local(ordinal = 0) ItemStack ingredient, @Local(ordinal = 1) LocalRef<ItemStack> result) {
        if (adapt$level == null) {
            return;
        }
        ItemStack chosen = BlockHooks.smelt(adapt$level, adapt$pos, items, ingredient, result.get());
        if (chosen == null) {
            callback.setReturnValue(false);
        } else if (chosen != result.get()) {
            result.set(chosen);
        }
    }
}
