package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Damage to paintings, leash knots and other block-attached entities; item frames post from their own override. */
@Mixin(BlockAttachedEntity.class)
public abstract class HangingDamageEvents {
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void adapt$damaging(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback,
                                @Local(argsOnly = true) LocalFloatRef damage) {
        if ((Object) this instanceof ItemFrame) {
            return;
        }
        if (!(((Entity) (Object) this).level() instanceof ServerLevel level)) {
            return;
        }
        float result = CombatHooks.damagingObject((Entity) (Object) this, level, source, amount);
        if (result < 0.0F) {
            callback.setReturnValue(false);
        } else if (result != amount) {
            damage.set(result);
        }
    }
}
