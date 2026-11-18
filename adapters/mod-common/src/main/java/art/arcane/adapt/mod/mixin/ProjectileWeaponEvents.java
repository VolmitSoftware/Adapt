package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/** Bow and crossbow shots, one event per projectile before it spawns; a cancelled shot uses no ammunition or durability. */
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponEvents {
    @WrapOperation(method = "shoot", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectile(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Ljava/util/function/Consumer;)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile adapt$shooting(Projectile projectile, ServerLevel level, ItemStack ammo, Consumer<Projectile> setup,
                                      Operation<Projectile> original, @Local(argsOnly = true, ordinal = 0) LivingEntity shooter,
                                      @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true, ordinal = 0) ItemStack weapon,
                                      @Local(argsOnly = true, ordinal = 0) float power) {
        if (EntityHooks.shooting(shooter, weapon, ammo, projectile, hand, power)) {
            return projectile;
        }
        return original.call(projectile, level, ammo, setup);
    }

    /** A shot a handler cancelled costs the weapon no durability. */
    @WrapOperation(method = "shoot", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void adapt$wearing(ItemStack weapon, int amount, LivingEntity shooter, EquipmentSlot slot, Operation<Void> original) {
        if (!EntityHooks.shotCancelled()) {
            original.call(weapon, amount, shooter, slot);
        }
    }
}
