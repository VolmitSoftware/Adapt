package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bow and crossbow shots, one event per aimed projectile before it is added to the world; a cancelled shot uses no
 * ammunition or durability.
 */
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponEvents {
    @WrapOperation(method = "shoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/world/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$shooting(ServerLevel level, Entity spawned, Operation<Boolean> original,
                                   @Local(argsOnly = true, ordinal = 0) LivingEntity shooter, @Local(argsOnly = true) InteractionHand hand,
                                   @Local(argsOnly = true, ordinal = 0) ItemStack weapon, @Local(ordinal = 1) ItemStack ammo,
                                   @Local(argsOnly = true, ordinal = 0) float power) {
        if (spawned instanceof Projectile projectile && EntityHooks.shooting(shooter, weapon, ammo, projectile, hand, power)) {
            return false;
        }
        return original.call(level, spawned);
    }

    /** A shot a handler cancelled costs the weapon no durability. */
    @WrapOperation(method = "shoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;FFZLnet/minecraft/world/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void adapt$wearing(ItemStack weapon, int amount, LivingEntity shooter, EquipmentSlot slot, Operation<Void> original) {
        if (!EntityHooks.shotCancelled()) {
            original.call(weapon, amount, shooter, slot);
        }
    }
}
