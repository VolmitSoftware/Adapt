package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.KnockbackHooks;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Two entities pushing each other apart on collision, one push event per side. */
@Mixin(Entity.class)
public abstract class EntityPushEvents {
    @WrapOperation(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void adapt$collisionPush(Entity pushed, double x, double y, double z, Operation<Void> original,
                                     @Local(argsOnly = true) Entity other) {
        if (!KnockbackHooks.active()) {
            original.call(pushed, x, y, z);
            return;
        }
        Entity self = (Entity) (Object) this;
        Vec3 impulse = KnockbackHooks.push(pushed, pushed == self ? other : self, EntityKnockbackEvent.Cause.PUSH, new Vec3(x, y, z));
        if (impulse != null) {
            original.call(pushed, impulse.x, impulse.y, impulse.z);
        }
    }
}
