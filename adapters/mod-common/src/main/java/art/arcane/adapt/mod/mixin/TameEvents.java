package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wolves, cats, parrots and every other ownable animal tamed by a player. */
@Mixin(TamableAnimal.class)
public abstract class TameEvents {
    @Inject(method = "tame", at = @At("HEAD"), cancellable = true)
    private void adapt$taming(Player player, CallbackInfo callback) {
        if (CombatHooks.taming((LivingEntity) (Object) this, player)) {
            callback.cancel();
        }
    }
}
