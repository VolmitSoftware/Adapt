package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Tilt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player standing on a big dripleaf: a denied physical interaction keeps the leaf level. */
@Mixin(BigDripleafBlock.class)
public abstract class BigDripleafEvents {
    @WrapOperation(method = "entityInside", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/BigDripleafBlock;setTiltAndScheduleTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/properties/Tilt;Lnet/minecraft/sounds/SoundEvent;)V"))
    private void adapt$tilting(BigDripleafBlock block, BlockState state, Level level, BlockPos pos, Tilt tilt, SoundEvent sound,
                               Operation<Void> original, @Local(argsOnly = true) Entity entity) {
        if (!InteractHooks.physical(entity, level, pos)) {
            original.call(block, state, level, pos, tilt, sound);
        }
    }
}
