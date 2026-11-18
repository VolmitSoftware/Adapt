package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player pickup, reached only once the pickup delay and target checks passed, and damage to dropped items. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityEvents {
    @Inject(method = "playerTouch", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private void adapt$pickingUp(Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer && EntityHooks.playerPickingUp(serverPlayer, (ItemEntity) (Object) this)) {
            callback.cancel();
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void adapt$damaging(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback,
                                @Local(argsOnly = true) LocalFloatRef damage) {
        float result = CombatHooks.damagingObject((ItemEntity) (Object) this, level, source, amount);
        if (result < 0.0F) {
            callback.setReturnValue(false);
        } else if (result != amount) {
            damage.set(result);
        }
    }
}
