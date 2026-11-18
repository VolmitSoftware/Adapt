package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Target reason for targets this goal sets, as Paper passes it. */
@Mixin(NearestAttackableTargetGoal.class)
public abstract class NearestTargetEvents {
    @Shadow
    protected LivingEntity target;

    @Inject(method = "start", at = @At("HEAD"))
    private void adapt$starting(CallbackInfo callback) {
        CombatHooks.targetReason(target instanceof Player ? EntityTargetEvent.Reason.CLOSEST_PLAYER : EntityTargetEvent.Reason.CLOSEST_ENTITY);
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void adapt$started(CallbackInfo callback) {
        CombatHooks.targetReason(null);
    }
}
