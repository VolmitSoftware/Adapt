package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.OwnedBy;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks a player's melee attack as critical and its sweep hits as sweeps for the damage events they
 * cause, and ties the player's food data to the player.
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

    @Shadow
    private boolean canCriticalAttack(Entity target) {
        throw new AssertionError();
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void adapt$attacking(Entity target, CallbackInfo callback) {
        CombatHooks.attacking(getAttackStrengthScale(0.5F) > 0.9F && canCriticalAttack(target));
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void adapt$attacked(Entity target, CallbackInfo callback) {
        CombatHooks.attacked();
    }

    @Inject(method = "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("HEAD"))
    private void adapt$sweepStarted(Entity target, float damage, DamageSource source, float strength, CallbackInfo callback) {
        CombatHooks.sweepStarted();
    }

    @Inject(method = "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("RETURN"))
    private void adapt$sweepEnded(Entity target, float damage, DamageSource source, float strength, CallbackInfo callback) {
        CombatHooks.sweepEnded();
    }
}
