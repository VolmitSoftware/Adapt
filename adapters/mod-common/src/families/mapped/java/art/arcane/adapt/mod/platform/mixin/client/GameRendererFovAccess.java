package art.arcane.adapt.mod.platform.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The field of view the level is drawn with, FOV effects included; private before 1.21.2. */
@Mixin(GameRenderer.class)
public interface GameRendererFovAccess {
    @Invoker("getFov")
    double adapt$fov(Camera camera, float partialTick, boolean useFovSetting);
}
