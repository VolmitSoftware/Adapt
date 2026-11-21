package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ActiveClientMixin {
    @Inject(method = "pauseIfInactive()V", at = @At("HEAD"), cancellable = true)
    private void adaptUiKeepRunning(CallbackInfo callback) {
        if (MacBackground.hidden()) {
            callback.cancel();
        }
    }
}
