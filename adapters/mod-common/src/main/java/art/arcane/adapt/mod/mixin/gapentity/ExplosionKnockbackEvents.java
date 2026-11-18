package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.KnockbackHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Explosion knockback on living entities; the result also drives the knockback packet sent to hit players. */
@Mixin(ServerExplosion.class)
public abstract class ExplosionKnockbackEvents {
    @ModifyExpressionValue(method = {"hurtEntities()V", "hurtEntities(Ljava/util/List;)V"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 adapt$explosionKnockback(Vec3 knockback, @Local Entity entity) {
        if (!KnockbackHooks.active()) {
            return knockback;
        }
        return KnockbackHooks.explosion(entity, ((ServerExplosion) (Object) this).getDirectSourceEntity(), knockback);
    }
}
