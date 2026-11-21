package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
    @Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
    private void adaptUiNoThrottle(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> callback) {
        if (MacBackground.hidden()) {
            callback.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
        }
    }
}
