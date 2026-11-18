package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.AiHooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Containers a copper golem accepts as a sorting target; a handler can refuse one vanilla allowed. */
@Mixin(TransportItemsBetweenContainers.class)
public abstract class TransportTargetEvents {
    @ModifyReturnValue(method = "isTargetValidToPick", at = @At("RETURN"))
    private TransportItemsBetweenContainers.TransportItemTarget adapt$validating(TransportItemsBetweenContainers.TransportItemTarget target,
                                                                              @Local(argsOnly = true) PathfinderMob body) {
        if (target == null || AiHooks.transportTarget(body, target.pos())) {
            return target;
        }
        return null;
    }
}
