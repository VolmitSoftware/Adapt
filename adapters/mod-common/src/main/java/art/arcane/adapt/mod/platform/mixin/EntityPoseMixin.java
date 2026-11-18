package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptFixedPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPoseMixin implements AdaptFixedPose {
    @Unique
    private boolean adapt$fixedPose;

    @Inject(method = "setPose", at = @At("HEAD"), cancellable = true)
    private void adapt$holdFixedPose(Pose pose, CallbackInfo callback) {
        if (adapt$fixedPose) {
            callback.cancel();
        }
    }

    @Override
    public boolean adapt$fixedPose() {
        return adapt$fixedPose;
    }

    @Override
    public void adapt$setFixedPose(boolean fixed) {
        adapt$fixedPose = fixed;
    }
}
