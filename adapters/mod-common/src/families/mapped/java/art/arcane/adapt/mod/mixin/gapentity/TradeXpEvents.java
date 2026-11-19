package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.TradeHooks;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips a trade's experience reward when a trade event turned it off. */
@Mixin({Villager.class, WanderingTrader.class})
public abstract class TradeXpEvents {
    @Inject(method = "rewardTradeXp(Lnet/minecraft/world/item/trading/MerchantOffer;)V", at = @At("HEAD"), cancellable = true)
    private void adapt$rewarding(MerchantOffer offer, CallbackInfo callback) {
        if (TradeHooks.xpSuppressed()) {
            callback.cancel();
        }
    }
}
