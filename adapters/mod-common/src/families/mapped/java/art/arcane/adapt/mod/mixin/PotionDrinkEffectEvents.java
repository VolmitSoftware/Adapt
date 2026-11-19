package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Effects from drinking a potion. */
@Mixin(PotionItem.class)
public abstract class PotionDrinkEffectEvents {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void adapt$causing(CallbackInfoReturnable<ItemStack> callback) {
        CombatHooks.effectCause(EntityPotionEffectEvent.Cause.POTION_DRINK, false);
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void adapt$caused(CallbackInfoReturnable<ItemStack> callback) {
        CombatHooks.effectCauseEnd();
    }
}
