package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every block change passes here; open capture frames remember the state it replaces. */
@Mixin(Level.class)
public abstract class LevelCaptureEvents {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void adapt$capturing(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> callback) {
        if (BlockCapture.active()) {
            BlockCapture.changing((Level) (Object) this, pos);
        }
    }
}
