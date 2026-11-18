package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.HarvestHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Holds back the drops of a berry harvest until its event decided. */
@Mixin(Block.class)
public abstract class HarvestLootEvents {
    @Inject(method = "dropFromBlockInteractLootTable", at = @At("HEAD"))
    private static void adapt$lootStarting(CallbackInfoReturnable<Boolean> callback) {
        HarvestHooks.lootStarting();
    }

    @Inject(method = "dropFromBlockInteractLootTable", at = @At("RETURN"))
    private static void adapt$lootDone(CallbackInfoReturnable<Boolean> callback) {
        HarvestHooks.lootDone();
    }

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD"), cancellable = true)
    private static void adapt$popping(Level level, BlockPos pos, ItemStack stack, CallbackInfo callback) {
        if (HarvestHooks.popping(level, pos, stack)) {
            callback.cancel();
        }
    }
}
