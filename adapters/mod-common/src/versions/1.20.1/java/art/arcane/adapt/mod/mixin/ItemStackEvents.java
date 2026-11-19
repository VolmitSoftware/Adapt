package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Durability loss of a player's tool or armour, after Unbreaking, before it is applied. The second point is reached
 * only for a player and a non-zero loss, the only case the hook changes.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEvents {
    @Inject(method = "hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z", at = @At("HEAD"))
    private void adapt$requested(int amount, RandomSource random, ServerPlayer player, CallbackInfoReturnable<Boolean> callback,
                                 @Share("requested") LocalIntRef requested) {
        requested.set(amount);
    }

    @Inject(method = "hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z", cancellable = true,
        at = @At(value = "FIELD", opcode = 178,
        target = "Lnet/minecraft/advancements/CriteriaTriggers;ITEM_DURABILITY_CHANGED:Lnet/minecraft/advancements/critereon/ItemDurabilityTrigger;"))
    private void adapt$damaging(int amount, RandomSource random, ServerPlayer player, CallbackInfoReturnable<Boolean> callback,
                                @Local(argsOnly = true) LocalIntRef damage, @Share("requested") LocalIntRef requested) {
        int changed = PlayerHooks.itemDamaging(player, (ItemStack) (Object) this, amount, requested.get());
        if (changed <= 0) {
            callback.setReturnValue(false);
        } else if (changed != amount) {
            damage.set(changed);
        }
    }
}
