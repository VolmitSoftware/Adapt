package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.block.BlockHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sculk sensors and shriekers accepting a vibration; entity listeners (wardens, allays) are not blocks. */
@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationListenerEvents {
    @Shadow
    @Final
    private VibrationSystem system;

    @Inject(method = "handleGameEvent", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Listener;scheduleVibration(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data;Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/level/gameevent/GameEvent$Context;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V"))
    private void adapt$receiving(ServerLevel level, GameEvent event, GameEvent.Context context, Vec3 source,
                                 CallbackInfoReturnable<Boolean> callback) {
        if (system instanceof BlockEntity blockEntity && !BlockHooks.vibration(level, blockEntity.getBlockPos(), event, context.sourceEntity())) {
            callback.setReturnValue(false);
        }
    }
}
