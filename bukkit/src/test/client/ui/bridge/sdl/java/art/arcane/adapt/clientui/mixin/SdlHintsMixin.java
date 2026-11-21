package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.MacBackground;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.TimeSource;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLHints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public abstract class SdlHintsMixin {
    @Inject(method = "initBackendSystem", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLInit;SDL_Init(I)Z", remap = false))
    private static void adaptUiBackgroundHints(CallbackInfoReturnable<TimeSource.NanoTimeSource> callback) {
        if (!MacBackground.hidden()) {
            return;
        }
        String[][] hints = {{SDLHints.SDL_HINT_MAC_BACKGROUND_APP, "1"}, {SDLHints.SDL_HINT_WINDOW_ACTIVATE_WHEN_SHOWN, "0"},
                {SDLHints.SDL_HINT_WINDOW_ACTIVATE_WHEN_RAISED, "0"}, {SDLHints.SDL_HINT_MOUSE_AUTO_CAPTURE, "0"}};
        for (String[] hint : hints) {
            if (!SDLHints.SDL_SetHintWithPriority(hint[0], hint[1], SDLHints.SDL_HINT_OVERRIDE)) {
                throw new IllegalStateException("Cannot set SDL hint " + hint[0] + ": " + SDLError.SDL_GetError());
            }
        }
    }
}
