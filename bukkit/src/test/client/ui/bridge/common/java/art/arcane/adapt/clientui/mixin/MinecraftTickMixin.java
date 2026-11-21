package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.UiBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void adaptUiTick(CallbackInfo callback) {
        UiBridge.tick((Minecraft) (Object) this);
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void adaptUiClose(CallbackInfo callback) {
        UiBridge.close();
    }
}
