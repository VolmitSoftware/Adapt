package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An enderman placing its carried block. Cancelled: it keeps carrying the block. */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class EndermanLeaveBlockEvents {
    @Shadow
    @Final
    private EnderMan enderman;

    @Inject(method = "tick()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
        cancellable = true)
    private void adapt$placing(CallbackInfo callback, @Local(ordinal = 0) BlockPos pos, @Local(ordinal = 2) BlockState carried) {
        if (ChangeBlockHooks.changing(enderman, pos, carried)) {
            callback.cancel();
        }
    }
}
