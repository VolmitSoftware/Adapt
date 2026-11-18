package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Horses, donkeys, mules, llamas and camels tamed by a player. */
@Mixin(AbstractHorse.class)
public abstract class HorseTameEvents {
    @Inject(method = "tameWithName", at = @At("HEAD"), cancellable = true)
    private void adapt$taming(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (CombatHooks.taming((LivingEntity) (Object) this, player)) {
            callback.setReturnValue(false);
        }
    }
}
