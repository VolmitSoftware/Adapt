package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Target reason for targets this goal sets, as Paper passes it. */
@Mixin(OwnerHurtByTargetGoal.class)
public abstract class OwnerHurtByTargetEvents {
    @Inject(method = "start", at = @At("HEAD"))
    private void adapt$starting(CallbackInfo callback) {
        CombatHooks.targetReason(EntityTargetEvent.Reason.TARGET_ATTACKED_OWNER);
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void adapt$started(CallbackInfo callback) {
        CombatHooks.targetReason(null);
    }
}
