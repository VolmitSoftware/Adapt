package art.arcane.adapt.clientqa.mixin;

import art.arcane.adapt.clientqa.ClientBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void beforeTick(CallbackInfo callback) {
        ClientBridge.tick((Minecraft) (Object) this);
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void afterRender(boolean render, CallbackInfo callback) {
        ClientBridge.frame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void closeBridge(CallbackInfo callback) {
        ClientBridge.close();
    }
}
