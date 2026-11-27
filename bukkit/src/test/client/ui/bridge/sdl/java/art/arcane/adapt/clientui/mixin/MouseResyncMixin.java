package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.3 re-reads the OS pointer (SDL_GetGlobalMouseState) on every screen change. The hidden client's OS pointer is
 * nowhere near the synthetic one, so without this a retitle or menu swap appears to move the cursor.
 */
@Mixin(MouseHandler.class)
public abstract class MouseResyncMixin {
    @Inject(method = "resyncMousePosition()V", at = @At("HEAD"), cancellable = true)
    private void adaptUiKeepSyntheticPointer(CallbackInfo callback) {
        if (MacBackground.hidden()) {
            callback.cancel();
        }
    }
}
