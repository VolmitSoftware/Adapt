package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.ModPlatform;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerTickMixin {
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void adapt$tick(BooleanSupplier haveTime, CallbackInfo callback) {
        ModPlatform.tick((MinecraftServer) (Object) this);
    }
}
