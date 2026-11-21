package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "grabMouse()V", at = @At("HEAD"), cancellable = true)
    private void adaptUiNoGrab(CallbackInfo callback) {
        if (MacBackground.hidden()) {
            callback.cancel();
        }
    }
}
