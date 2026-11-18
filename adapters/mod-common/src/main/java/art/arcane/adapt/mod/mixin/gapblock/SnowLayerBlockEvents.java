package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snow layers melting next to light, before the layer drops anything. */
@Mixin(SnowLayerBlock.class)
public abstract class SnowLayerBlockEvents {
    @Inject(method = "randomTick", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/SnowLayerBlock;dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void adapt$melting(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (!BlockHooks.fadeToAir(level, pos)) {
            callback.cancel();
        }
    }
}
