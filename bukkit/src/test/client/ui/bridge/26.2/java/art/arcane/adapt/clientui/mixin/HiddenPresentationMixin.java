package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.SurfaceException;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class HiddenPresentationMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V", remap = false))
    private void adaptUiNoShow(long window) {
        if (!MacBackground.hidden()) {
            GLFW.glfwShowWindow(window);
        }
    }

    @Redirect(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;configure(Lcom/mojang/blaze3d/systems/GpuSurface$Configuration;)V"))
    private void adaptUiConfigure(GpuSurface surface, GpuSurface.Configuration configuration) throws SurfaceException {
        if (!MacBackground.hidden()) {
            surface.configure(configuration);
        }
    }

    @Redirect(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;acquireNextTexture()V"))
    private void adaptUiAcquire(GpuSurface surface) throws SurfaceException {
        if (!MacBackground.hidden()) {
            surface.acquireNextTexture();
        }
    }
}
