package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.neoforged.neoforge.client.loading.earlydisplay.EarlyWindowHandoff", remap = false)
public abstract class NeoForgeHandoffMixin {
    @Redirect(method = "completeWindowHandoff", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_ShowWindow(J)Z"), require = 1)
    private static boolean adaptUiNoShow(long window) {
        return MacBackground.hidden() || SDLVideo.SDL_ShowWindow(window);
    }

    @Redirect(method = "completeWindowHandoff", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_MaximizeWindow(J)Z"), require = 0)
    private static boolean adaptUiNoMaximize(long window) {
        return MacBackground.hidden() || SDLVideo.SDL_MaximizeWindow(window);
    }
}
