package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player releasing the item in use, such as a drawn bow. */
@Mixin(LivingEntity.class)
public abstract class ReleaseItemEvents {
    @Inject(method = "releaseUsingItem", at = @At("HEAD"))
    private void adapt$releasing(CallbackInfo callback) {
        PlayerGapHooks.releasing((LivingEntity) (Object) this);
    }
}
