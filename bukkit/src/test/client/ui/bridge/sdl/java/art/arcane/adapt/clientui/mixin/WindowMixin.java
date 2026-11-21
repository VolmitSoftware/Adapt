package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import com.mojang.blaze3d.platform.Window;
import com.mojang.renderpearl.api.device.GpuBackend;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class WindowMixin {
    private static final long UNWANTED = SDLVideo.SDL_WINDOW_FULLSCREEN | SDLVideo.SDL_WINDOW_HIGH_PIXEL_DENSITY
            | SDLVideo.SDL_WINDOW_MINIMIZED | SDLVideo.SDL_WINDOW_MAXIMIZED | SDLVideo.SDL_WINDOW_MOUSE_GRABBED
            | SDLVideo.SDL_WINDOW_KEYBOARD_GRABBED | SDLVideo.SDL_WINDOW_MOUSE_CAPTURE | SDLVideo.SDL_WINDOW_MOUSE_RELATIVE_MODE;

    @Redirect(method = {"createWindow", "createWindowStatic"}, at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), require = 1, allow = 1)
    private static long adaptUiHiddenWindow(GpuBackend backend, String title, int width, int height, long flags) {
        if (!MacBackground.hidden()) {
            return backend.createWindow(title, width, height, flags);
        }
        return backend.createWindow(title, width, height, (flags & ~UNWANTED) | SDLVideo.SDL_WINDOW_HIDDEN | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adaptUiBackground(CallbackInfo callback) {
        MacBackground.apply();
    }

    @Inject(method = {"setMode()V", "restoreWindow()V", "updateWindowMouseGrab()V"}, at = @At("HEAD"), cancellable = true)
    private void adaptUiStayHidden(CallbackInfo callback) {
        if (MacBackground.hidden()) {
            callback.cancel();
        }
    }

    @Inject(method = {"applyFullscreen()Z", "applySdlBorderlessFullscreen()Z", "applyWindowed()Z", "applyBorderlessFullscreenWindow()Z"},
            at = @At("HEAD"), cancellable = true)
    private void adaptUiNoModeChange(CallbackInfoReturnable<Boolean> callback) {
        if (MacBackground.hidden()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "isIconified()Z", at = @At("HEAD"), cancellable = true)
    private void adaptUiKeepRendering(CallbackInfoReturnable<Boolean> callback) {
        if (MacBackground.hidden()) {
            callback.setReturnValue(false);
        }
    }
}
