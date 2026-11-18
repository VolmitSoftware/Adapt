package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.TradeHooks;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets a trade event keep an offer's use count unchanged. */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferUsesEvents implements TradeHooks.MerchantOfferUses {
    @Shadow
    private int uses;

    @Override
    public void adapt$uses(int uses) {
        this.uses = uses;
    }
}
