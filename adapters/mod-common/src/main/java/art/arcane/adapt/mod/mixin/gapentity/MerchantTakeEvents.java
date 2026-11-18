package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.TradeHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A trade result taken from a villager or wandering trader. */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantTakeEvents {
    @Shadow
    @Final
    private MerchantContainer slots;

    @Shadow
    @Final
    private Merchant merchant;

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void adapt$taking(Player player, ItemStack carried, CallbackInfo callback) {
        if (TradeHooks.taking(player, carried, merchant, slots)) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "onTake", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V"))
    private void adapt$notifying(Merchant target, MerchantOffer offer, Operation<Void> original) {
        TradeHooks.notifying(target, offer, () -> original.call(target, offer));
    }
}
