package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.ChangeBlockHooks;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zombies and vindicators breaking a door down. Cancelled: the door stays. */
@Mixin(BreakDoorGoal.class)
public abstract class DoorBreakEvents extends DoorInteractGoal {
    private DoorBreakEvents(Mob mob) {
        super(mob);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
        cancellable = true)
    private void adapt$breaking(CallbackInfo callback) {
        if (ChangeBlockHooks.clearing(mob, doorPos)) {
            callback.cancel();
        }
    }
}
