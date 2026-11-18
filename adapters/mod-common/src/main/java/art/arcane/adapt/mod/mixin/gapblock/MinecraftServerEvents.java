package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.world.WorldHooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/** Worlds loading at start, closing at stop, and data pack reloads. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerEvents {
    @Inject(method = "createLevels", at = @At("TAIL"))
    private void adapt$levelsCreated(CallbackInfo callback) {
        WorldHooks.levelsCreated((MinecraftServer) (Object) this);
    }

    @WrapOperation(method = "stopServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;close()V"))
    private void adapt$closing(ServerLevel level, Operation<Void> original) {
        WorldHooks.closing(level);
        original.call(level);
    }

    @ModifyReturnValue(method = "reloadResources", at = @At("RETURN"))
    private CompletableFuture<Void> adapt$reloading(CompletableFuture<Void> future) {
        return WorldHooks.reloading(future);
    }
}
