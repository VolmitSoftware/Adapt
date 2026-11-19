package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * Bow shots, posted once the arrow is aimed and before the bow takes durability; a cancelled shot costs no durability,
 * spawns no arrow and returns its ammunition. Crossbow shots post from {@link CrossbowShotEvents}.
 */
@Mixin(BowItem.class)
public abstract class ProjectileWeaponEvents {
    @WrapOperation(method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"))
    private void adapt$shooting(ItemStack weapon, int amount, LivingEntity shooter, Consumer<LivingEntity> broken, Operation<Void> original,
                                @Local AbstractArrow arrow, @Local(ordinal = 1) ItemStack ammo, @Local(ordinal = 0) float power) {
        if (!EntityHooks.shooting(shooter, weapon, ammo, arrow, shooter.getUsedItemHand(), power * 3.0F)) {
            original.call(weapon, amount, shooter, broken);
        }
    }

    @WrapOperation(method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$spawning(Level level, Entity arrow, Operation<Boolean> original) {
        return !EntityHooks.shotCancelled() && original.call(level, arrow);
    }
}
