package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge 52 replaces the arrow's hitTargetOrDeflectSelf call in tick with its impact event and calls onHit itself, so
 * arrow hits never reach the projectile hook there. Vanilla and NeoForge make no such call, so nothing matches there.
 */
@Mixin(AbstractArrow.class)
public abstract class ArrowImpactEvents {
    @WrapWithCondition(method = "tick()V", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private boolean adapt$arrowHit(AbstractArrow arrow, HitResult hit) {
        return !EntityHooks.projectileHit(arrow, hit);
    }
}
