package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The player and hand behind an entity interaction, for shearing. */
@Mixin(Player.class)
public abstract class InteractOnEvents {
    @Inject(method = "interactOn", at = @At("HEAD"))
    private void adapt$interacting(Entity entity, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> callback) {
        PlayerGapHooks.interacting((Player) (Object) this, entity, hand);
    }

    @Inject(method = "interactOn", at = @At("RETURN"))
    private void adapt$interacted(Entity entity, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> callback) {
        PlayerGapHooks.interacted((Player) (Object) this);
    }
}
