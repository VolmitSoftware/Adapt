package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.InteractHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player stepping on a sculk sensor: a denied physical interaction schedules no vibration. */
@Mixin(SculkSensorBlock.class)
public abstract class SculkSensorEvents {
    @WrapOperation(method = "stepOn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Listener;forceScheduleVibration(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;)V"))
    private void adapt$stepping(VibrationSystem.Listener listener, ServerLevel level, Holder<GameEvent> event, GameEvent.Context context,
                                Vec3 position, Operation<Void> original, @Local(argsOnly = true) Entity entity,
                                @Local(argsOnly = true) BlockPos pos) {
        if (!InteractHooks.physical(entity, level, pos)) {
            original.call(listener, level, event, context, position);
        }
    }
}
