package art.arcane.adapt.clientqa.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Window.class)
public interface WindowAccessor {
    @Invoker("onFramebufferResize")
    void framebufferResized(long handle, int width, int height);
}
