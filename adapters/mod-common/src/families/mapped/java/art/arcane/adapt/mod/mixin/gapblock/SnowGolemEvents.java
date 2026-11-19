package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The snow trail a snow golem leaves. */
@Mixin(SnowGolem.class)
public abstract class SnowGolemEvents {
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean adapt$forming(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        return BlockHooks.form(level, pos, state, (SnowGolem) (Object) this) && original.call(level, pos, state);
    }
}
