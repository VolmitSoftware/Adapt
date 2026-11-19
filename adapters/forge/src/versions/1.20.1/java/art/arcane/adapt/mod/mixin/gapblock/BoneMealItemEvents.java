package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.GrowthHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bone meal on a block (Forge's applyBonemeal, which growCrop and useOn both call) and on water. The growth runs inside a capture frame; a cancel undoes it and keeps the bone meal.
 */
@Mixin(BoneMealItem.class)
public abstract class BoneMealItemEvents {
    @Inject(method = "useOn", at = @At("HEAD"))
    private void adapt$using(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        GrowthHooks.fertilizer(context.getPlayer());
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void adapt$used(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        GrowthHooks.fertilizer(null);
    }

    @Inject(method = "applyBonemeal", remap = false, require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BonemealableBlock;performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", remap = true))
    private static void adapt$fertilizing(CallbackInfoReturnable<Boolean> callback, @Local(argsOnly = true) Level level) {
        GrowthHooks.fertilizing(level);
    }

    @Inject(method = "applyBonemeal", remap = false, require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BonemealableBlock;performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", shift = At.Shift.AFTER, remap = true))
    private static void adapt$fertilized(CallbackInfoReturnable<Boolean> callback, @Local(argsOnly = true) Level level,
                                         @Local(argsOnly = true) BlockPos pos) {
        GrowthHooks.fertilized(level, pos);
    }

    @WrapOperation(method = "applyBonemeal", remap = false, require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V", remap = true))
    private static void adapt$consuming(ItemStack stack, int amount, Operation<Void> original) {
        if (!GrowthHooks.keepBonemeal()) {
            original.call(stack, amount);
        }
    }

    @Inject(method = "growWaterPlant", at = @At("HEAD"))
    private static void adapt$plantingWater(ItemStack stack, Level level, BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> callback) {
        GrowthHooks.plantingWater(stack, level);
    }

    @Inject(method = "growWaterPlant", at = @At("RETURN"))
    private static void adapt$plantedWater(ItemStack stack, Level level, BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> callback) {
        GrowthHooks.plantedWater(stack, level, pos);
    }
}
