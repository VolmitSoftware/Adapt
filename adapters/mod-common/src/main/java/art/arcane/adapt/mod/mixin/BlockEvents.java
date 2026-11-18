package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block experience inside a player break, replaced when a break handler set it. */
@Mixin(Block.class)
public abstract class BlockEvents {
    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void adapt$experience(ServerLevel level, BlockPos pos, int amount, CallbackInfo callback) {
        if (InteractHooks.blockExperience(level, pos)) {
            callback.cancel();
        }
    }
}
