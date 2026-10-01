package art.arcane.adapt.clientqa.mixin;

import art.arcane.adapt.clientqa.ClientBridge;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    private static final boolean HIDDEN = Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true"));

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"))
    private void showWindow(long handle) {
        if (!HIDDEN) {
            GLFW.glfwShowWindow(handle);
        }
    }

    @Redirect(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;isMinimized()Z"))
    private boolean skipHiddenPresentation(Window window) {
        return HIDDEN || window.isMinimized();
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean attackMouseGrabbed(MouseHandler mouse) {
        return mouse.isMouseGrabbed() || ClientBridge.hasActiveAttackLease();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void beforeTick(CallbackInfo callback) {
        ClientBridge.tick((Minecraft) (Object) this);
    }

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void beforeRender(boolean render, CallbackInfo callback) {
        ClientBridge.updateLook((Minecraft) (Object) this);
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void afterRender(boolean render, CallbackInfo callback) {
        ClientBridge.frame();
        ClientBridge.captureFrame((Minecraft) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void closeBridge(CallbackInfo callback) {
        ClientBridge.close();
    }
}
