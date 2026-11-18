package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Orb pickup: what is left after Mending, before it becomes player experience. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbEvents {
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;giveExperiencePoints(I)V"))
    private void adapt$gaining(Player player, int amount, Operation<Void> original) {
        original.call(player, PlayerHooks.experienceGained(player, amount));
    }
}
