package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.EntityHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Damage, death drops, healing, effects and finishing an item use for every living entity. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEvents {
    @Shadow
    public abstract InteractionHand getUsedItemHand();

    /** Players reach this through their own overrides, after their invulnerability and difficulty adjustments. */
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void adapt$damaging(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback,
                                @Local(argsOnly = true) LocalFloatRef damage) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }
        float result = CombatHooks.damaging(self, level, source, amount);
        if (result < 0.0F) {
            callback.setReturnValue(false);
        } else if (result != amount) {
            damage.set(result);
        }
    }

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"))
    private void adapt$damaged(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level() instanceof ServerLevel) {
            CombatHooks.damaged(self);
        }
    }

    /** Armour still wears down on a hit that ignores reductions; only its reduction is dropped. */
    @Inject(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F", at = @At("RETURN"), cancellable = true)
    private void adapt$armorReduction(DamageSource source, float amount, CallbackInfoReturnable<Float> callback) {
        if (CombatHooks.bypassing((LivingEntity) (Object) this)) {
            callback.setReturnValue(amount);
        }
    }

    @Inject(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F", at = @At("HEAD"), cancellable = true)
    private void adapt$magicReduction(DamageSource source, float amount, CallbackInfoReturnable<Float> callback) {
        if (CombatHooks.bypassing((LivingEntity) (Object) this)) {
            callback.setReturnValue(amount);
        }
    }

    /** A hit that ignores reductions is never blocked by a shield. */
    @WrapOperation(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean adapt$shieldBlocking(LivingEntity entity, DamageSource source, Operation<Boolean> original) {
        return !CombatHooks.bypassing(entity) && original.call(entity, source);
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void adapt$deathLootStarting(ServerLevel level, DamageSource source, CallbackInfo callback) {
        EntityHooks.deathLootStarting((LivingEntity) (Object) this, level);
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("TAIL"))
    private void adapt$deathLootDone(ServerLevel level, DamageSource source, CallbackInfo callback) {
        EntityHooks.deathLootDone((LivingEntity) (Object) this, level, source);
    }

    @WrapOperation(method = "dropExperience(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void adapt$deathExperience(ServerLevel level, Vec3 position, int amount, Operation<Void> original) {
        if (!EntityHooks.deathExperience((LivingEntity) (Object) this, amount)) {
            original.call(level, position, amount);
        }
    }

    @Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
    private void adapt$healing(float amount, CallbackInfo callback, @Local(argsOnly = true) LocalFloatRef heal) {
        float result = CombatHooks.healing((LivingEntity) (Object) this, amount);
        if (result < 0.0F) {
            callback.cancel();
        } else if (result != amount) {
            heal.set(result);
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
        cancellable = true)
    private void adapt$effectAdding(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> callback) {
        if (CombatHooks.effectAdding((LivingEntity) (Object) this, effect, source)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "removeEffect(Lnet/minecraft/core/Holder;)Z", at = @At("HEAD"), cancellable = true)
    private void adapt$effectRemoving(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> callback) {
        if (CombatHooks.effectRemoving((LivingEntity) (Object) this, effect)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "removeAllEffects()Z", at = @At("HEAD"), cancellable = true)
    private void adapt$effectsClearing(CallbackInfoReturnable<Boolean> callback) {
        Boolean result = CombatHooks.effectsClearing((LivingEntity) (Object) this);
        if (result != null) {
            callback.setReturnValue(result);
        }
    }

    @Inject(method = "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"),
        cancellable = true)
    private void adapt$effectForcing(MobEffectInstance effect, Entity source, CallbackInfo callback) {
        if (CombatHooks.effectAdding((LivingEntity) (Object) this, effect, source)) {
            callback.cancel();
        }
    }

    /** An effect that ran out on the server; a cancel keeps it for another tick, as Paper's expiration event does. */
    @WrapOperation(method = "tickEffects()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/effect/MobEffectInstance;tick(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z"))
    private boolean adapt$effectTicking(MobEffectInstance effect, LivingEntity entity, Runnable onUpdate, Operation<Boolean> original) {
        boolean active = original.call(effect, entity, onUpdate);
        return active || entity.level() instanceof ServerLevel && CombatHooks.effectExpiring(entity, effect);
    }

    /** Ties a brain to its entity the first time anything asks for it, so its memory hooks know whose target changed. */
    @ModifyReturnValue(method = "getBrain()Lnet/minecraft/world/entity/ai/Brain;", at = @At("RETURN"))
    private Brain<?> adapt$brain(Brain<?> brain) {
        if (brain instanceof OwnedBy owned && owned.adapt$owner() == null) {
            owned.adapt$owner((LivingEntity) (Object) this);
        }
        return brain;
    }

    /** Finishing an eat or drink: a cancel keeps the item and stops the use, as Paper's consume event does. */
    @WrapOperation(method = "completeUsingItem()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack adapt$consuming(ItemStack using, Level level, LivingEntity user, Operation<ItemStack> original) {
        if (!(user instanceof ServerPlayer player)) {
            return original.call(using, level, user);
        }
        PlayerItemConsumeEvent event = PlayerHooks.consuming(player, using, getUsedItemHand());
        if (event == null) {
            return original.call(using, level, user);
        }
        if (event.isCancelled()) {
            player.containerMenu.sendAllDataToRemote();
            return using;
        }
        ItemStack result = original.call(PlayerHooks.consumed(event, using), level, user);
        return PlayerHooks.consumeResult(event, result);
    }
}
