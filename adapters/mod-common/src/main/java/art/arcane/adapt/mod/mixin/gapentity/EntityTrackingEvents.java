package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.EntityLifeHooks;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paper's add-to-world and remove-from-world: an entity starts or stops being tracked by its level. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityTrackingEvents {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void adapt$added(Entity entity, CallbackInfo callback) {
        EntityLifeHooks.tracking(entity, EntityWorldEvent.Phase.ADD);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void adapt$removed(Entity entity, CallbackInfo callback) {
        EntityLifeHooks.tracking(entity, EntityWorldEvent.Phase.REMOVE);
    }
}
