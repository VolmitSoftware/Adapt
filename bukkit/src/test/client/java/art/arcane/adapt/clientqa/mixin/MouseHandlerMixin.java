package art.arcane.adapt.clientqa.mixin;

import art.arcane.adapt.clientqa.ClientBridge;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    private static final boolean HIDDEN = Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true"));

    @Inject(method = {"grabMouse", "releaseMouse"}, at = @At("HEAD"), cancellable = true)
    private void preserveDesktopCursor(CallbackInfo callback) {
        if (HIDDEN) {
            callback.cancel();
        }
    }

    @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
    private void cursorX(Window window, CallbackInfoReturnable<Double> callback) {
        if (ClientBridge.hasCursor()) {
            callback.setReturnValue(ClientBridge.cursorX());
        }
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
    private void cursorY(Window window, CallbackInfoReturnable<Double> callback) {
        if (ClientBridge.hasCursor()) {
            callback.setReturnValue(ClientBridge.cursorY());
        }
    }
}
