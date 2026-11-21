package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.device.SurfaceException;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class HiddenPresentationMixin {
    @Redirect(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuSurface;configure(Lcom/mojang/renderpearl/api/device/GpuSurface$Configuration;)V"))
    private void adaptUiConfigure(GpuSurface surface, GpuSurface.Configuration configuration) throws SurfaceException {
        if (!MacBackground.hidden()) {
            surface.configure(configuration);
        }
    }

    @Redirect(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuSurface;acquireNextTexture()V"))
    private void adaptUiAcquire(GpuSurface surface) throws SurfaceException {
        if (!MacBackground.hidden()) {
            surface.acquireNextTexture();
        }
    }
}
