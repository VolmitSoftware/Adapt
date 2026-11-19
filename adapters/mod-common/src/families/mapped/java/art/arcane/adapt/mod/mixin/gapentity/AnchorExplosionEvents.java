package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ExplodingBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The removed respawn anchor behind an anchor explosion. */
@Mixin(RespawnAnchorBlock.class)
public abstract class AnchorExplosionEvents {
    @Inject(method = "explode(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private void adapt$exploding(BlockState state, Level level, BlockPos pos, CallbackInfo callback) {
        ExplodingBlocks.exploding(state);
    }

    @Inject(method = "explode(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private void adapt$exploded(BlockState state, Level level, BlockPos pos, CallbackInfo callback) {
        ExplodingBlocks.exploded();
    }
}
