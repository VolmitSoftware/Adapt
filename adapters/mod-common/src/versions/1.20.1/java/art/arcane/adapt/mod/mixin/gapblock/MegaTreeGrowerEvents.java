package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.GrowthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractMegaTreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Four saplings growing into a mega tree, which skips the single-tree growTree that TreeGrowerEvents watches. */
@Mixin(AbstractMegaTreeGrower.class)
public abstract class MegaTreeGrowerEvents {
    @Inject(method = "placeMega", at = @At("HEAD"))
    private void adapt$growing(ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState state, RandomSource random, int offsetX,
                               int offsetZ, CallbackInfoReturnable<Boolean> callback) {
        GrowthHooks.growing(level);
    }

    @Inject(method = "placeMega", at = @At("RETURN"), cancellable = true)
    private void adapt$grown(ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState state, RandomSource random, int offsetX,
                             int offsetZ, CallbackInfoReturnable<Boolean> callback) {
        if (!GrowthHooks.grown(level, pos)) {
            callback.setReturnValue(false);
        }
    }
}
