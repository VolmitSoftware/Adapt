package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.EndermanHooks;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An enderman deciding whether a player provokes it by staring. */
@Mixin(EnderMan.class)
public abstract class EndermanStareEvents {
    @Inject(method = "isLookingAtMe(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("RETURN"), cancellable = true)
    private void adapt$staring(Player player, CallbackInfoReturnable<Boolean> callback) {
        boolean provoked = callback.getReturnValueZ();
        boolean result = EndermanHooks.staring((EnderMan) (Object) this, player, provoked);
        if (result != provoked) {
            callback.setReturnValue(result);
        }
    }
}
