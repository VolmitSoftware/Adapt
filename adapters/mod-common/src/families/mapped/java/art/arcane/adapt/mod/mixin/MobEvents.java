package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob targeting and item pickup. */
@Mixin(Mob.class)
public abstract class MobEvents {
    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"))
    private void adapt$targeting(LivingEntity target, CallbackInfo callback, @Local(argsOnly = true) LocalRef<LivingEntity> chosen) {
        LivingEntity result = CombatHooks.targeting((Mob) (Object) this, target);
        if (result != target) {
            chosen.set(result);
        }
    }

    @Inject(method = "pickUpItem(Lnet/minecraft/world/entity/item/ItemEntity;)V", at = @At("HEAD"), cancellable = true)
    private void adapt$pickingUp(ItemEntity item, CallbackInfo callback) {
        if (EntityHooks.mobPickingUp((Mob) (Object) this, item)) {
            callback.cancel();
        }
    }
}
