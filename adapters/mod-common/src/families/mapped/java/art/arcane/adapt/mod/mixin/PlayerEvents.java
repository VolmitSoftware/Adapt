package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import art.arcane.adapt.mod.event.PlayerHooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks a player's melee attack as critical and its sweep hits as sweeps for the damage events they
 * cause, ties the player's food data to the player, and carries the player drops and peaceful food refill
 * that live in the player class before 1.21.2.
 */
@Mixin(Player.class)
public abstract class PlayerEvents {
    @Shadow
    protected FoodData foodData;

    @Shadow
    public abstract float getAttackStrengthScale(float partialTicks);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void adapt$created(CallbackInfo callback) {
        ((OwnedBy) foodData).adapt$owner((Player) (Object) this);
    }

    /** The critical-hit test the attack inlines on these versions, before any loader event adjusts it. */
    @Unique
    private boolean adapt$canCriticalAttack(Entity target) {
        Player self = (Player) (Object) this;
        return self.fallDistance > 0.0F && !self.onGround() && !self.onClimbable() && !self.isInWater()
            && !self.hasEffect(MobEffects.BLINDNESS) && !self.isPassenger() && target instanceof LivingEntity && !self.isSprinting();
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void adapt$attacking(Entity target, CallbackInfo callback) {
        CombatHooks.attacking(getAttackStrengthScale(0.5F) > 0.9F && adapt$canCriticalAttack(target));
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void adapt$attacked(Entity target, CallbackInfo callback) {
        CombatHooks.attacked();
    }

    /** Each sweep hit of the attack's inlined sweep loop, the only living-entity hurt call in the attack. */
    @WrapOperation(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean adapt$sweeping(LivingEntity victim, DamageSource source, float amount, Operation<Boolean> original) {
        CombatHooks.sweepStarted();
        try {
            return original.call(victim, source, amount);
        } finally {
            CombatHooks.sweepEnded();
        }
    }

    /**
     * Items a player throws or drops, once built and before the server player adds them to the world or the
     * loader's capture; a cancelled drop drops nothing.
     */
    @ModifyReturnValue(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private ItemEntity adapt$dropping(ItemEntity item) {
        if ((Object) this instanceof ServerPlayer player && item != null && PlayerHooks.dropping(player, item)) {
            return null;
        }
        return item;
    }

    /** Peaceful difficulty refilling the food bar. */
    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"))
    private void adapt$refilling(FoodData food, int level, Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer player)) {
            original.call(food, level);
            return;
        }
        int chosen = PlayerHooks.hungering(player, level);
        if (chosen >= 0) {
            original.call(food, chosen);
        }
    }
}
