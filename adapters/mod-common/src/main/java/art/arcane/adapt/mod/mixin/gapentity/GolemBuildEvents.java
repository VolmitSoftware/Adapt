package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.SpawnHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snow, iron and copper golems built from a pumpkin pattern spawn as BUILD_*. */
@Mixin(CarvedPumpkinBlock.class)
public abstract class GolemBuildEvents {
    @Inject(method = "trySpawnGolem", at = @At("HEAD"))
    private void adapt$building(Level level, BlockPos pos, CallbackInfo callback) {
        SpawnHooks.golemBuildStarted();
    }

    @Inject(method = "trySpawnGolem", at = @At("RETURN"))
    private void adapt$built(Level level, BlockPos pos, CallbackInfo callback) {
        SpawnHooks.golemBuildEnded();
    }
}
