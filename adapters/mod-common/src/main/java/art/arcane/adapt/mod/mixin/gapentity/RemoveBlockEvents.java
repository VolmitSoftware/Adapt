package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zombies stomping turtle eggs. Cancelled: the egg stays. */
@Mixin(RemoveBlockGoal.class)
public abstract class RemoveBlockEvents {
    @Shadow
    @Final
    private Mob removerMob;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
        cancellable = true)
    private void adapt$removing(CallbackInfo callback, @Local(ordinal = 1) BlockPos pos) {
        if (ChangeBlockHooks.clearing(removerMob, pos)) {
            callback.cancel();
        }
    }
}
