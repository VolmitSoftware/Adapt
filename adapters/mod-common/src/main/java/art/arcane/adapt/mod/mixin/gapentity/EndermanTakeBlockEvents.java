package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An enderman picking up a block. Cancelled: the block stays and the enderman carries nothing. */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockEvents {
    @Shadow
    @Final
    private Enderman enderman;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
        cancellable = true)
    private void adapt$taking(CallbackInfo callback, @Local(ordinal = 0) BlockPos pos) {
        if (ChangeBlockHooks.clearing(enderman, pos)) {
            callback.cancel();
        }
    }
}
