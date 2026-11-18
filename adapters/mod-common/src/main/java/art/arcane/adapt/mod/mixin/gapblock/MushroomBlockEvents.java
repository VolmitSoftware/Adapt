package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.GrowthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Small mushrooms growing into huge ones. */
@Mixin(MushroomBlock.class)
public abstract class MushroomBlockEvents {
    @Inject(method = "growMushroom", at = @At("HEAD"))
    private void adapt$growing(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfoReturnable<Boolean> callback) {
        GrowthHooks.growing(level);
    }

    @Inject(method = "growMushroom", at = @At("RETURN"), cancellable = true)
    private void adapt$grown(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfoReturnable<Boolean> callback) {
        if (!GrowthHooks.grown(level, pos)) {
            callback.setReturnValue(false);
        }
    }
}
