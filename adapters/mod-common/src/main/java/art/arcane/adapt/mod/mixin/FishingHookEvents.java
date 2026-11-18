package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bites, lures, missed bites and reeling in: a hooked entity, each caught item with its orb, the hook in the ground or empty. */
@Mixin(FishingHook.class)
public abstract class FishingHookEvents {
    @Shadow
    private Entity hookedIn;

    @Inject(method = "retrieve", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/FishingHook;pullEntity(Lnet/minecraft/world/entity/Entity;)V"))
    private void adapt$caughtEntity(ItemStack rod, CallbackInfoReturnable<Integer> callback) {
        FishingHook self = (FishingHook) (Object) this;
        if (PlayerHooks.fishCaughtEntity(self, self.getPlayerOwner(), hookedIn)) {
            callback.setReturnValue(0);
        }
    }

    @Inject(method = "retrieve", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 0))
    private void adapt$caught(ItemStack rod, CallbackInfoReturnable<Integer> callback, @Local ItemEntity caught) {
        FishingHook self = (FishingHook) (Object) this;
        if (PlayerHooks.fishCaught(self, self.getPlayerOwner(), caught)) {
            callback.setReturnValue(0);
        }
    }

    @WrapOperation(method = "retrieve", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 1))
    private boolean adapt$caughtExperience(Level level, Entity entity, Operation<Boolean> original) {
        if (!(entity instanceof ExperienceOrb orb)) {
            return original.call(level, entity);
        }
        ExperienceOrb chosen = PlayerHooks.fishExperience(orb);
        return chosen != null && original.call(level, chosen);
    }

    /** A fish takes the bait: a cancel lets it swim off without biting. */
    @Inject(method = "catchingFish", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/FishingHook;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", ordinal = 0))
    private void adapt$biting(BlockPos pos, CallbackInfo callback) {
        if (PlayerHooks.fishState((FishingHook) (Object) this, PlayerFishEvent.State.BITE)) {
            callback.cancel();
        }
    }

    /** A fish starts swimming toward the hook: a cancel restarts the wait. */
    @Inject(method = "catchingFish", cancellable = true, at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/projectile/FishingHook;fishAngle:F", opcode = Opcodes.PUTFIELD, ordinal = 1))
    private void adapt$luring(BlockPos pos, CallbackInfo callback) {
        if (PlayerHooks.fishState((FishingHook) (Object) this, PlayerFishEvent.State.LURED)) {
            callback.cancel();
        }
    }

    /** The bite window closed without a reel-in. */
    @Inject(method = "catchingFish", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/projectile/FishingHook;timeUntilLured:I", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void adapt$missed(BlockPos pos, CallbackInfo callback) {
        PlayerHooks.fishState((FishingHook) (Object) this, PlayerFishEvent.State.FAILED_ATTEMPT);
    }

    @Inject(method = "retrieve", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/FishingHook;discard()V"))
    private void adapt$reeling(ItemStack rod, CallbackInfoReturnable<Integer> callback, @Local(ordinal = 0) int caught) {
        if (PlayerHooks.reeling((FishingHook) (Object) this, caught)) {
            callback.setReturnValue(0);
        }
    }
}
