package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Projectile launches, and death and break drops held back while their events decide. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEvents {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void adapt$adding(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (EntityHooks.adding((ServerLevel) (Object) this, entity)) {
            callback.setReturnValue(false);
        }
    }
}
