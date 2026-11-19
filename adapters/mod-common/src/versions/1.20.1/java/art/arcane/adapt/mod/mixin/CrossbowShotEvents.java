package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.EntityHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * Crossbow shots by players and mobs, one event per aimed projectile before the crossbow takes durability; a cancelled
 * shot costs no durability and spawns nothing.
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowShotEvents {
    @WrapOperation(method = "shootProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;FZFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"))
    private static void adapt$shooting(ItemStack weapon, int amount, LivingEntity shooter, Consumer<LivingEntity> broken, Operation<Void> original,
                                       @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true, ordinal = 1) ItemStack ammo,
                                       @Local(argsOnly = true, ordinal = 1) float power, @Local Projectile projectile) {
        if (!EntityHooks.shooting(shooter, weapon, ammo, projectile, hand, power)) {
            original.call(weapon, amount, shooter, broken);
        }
    }

    @WrapOperation(method = "shootProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;FZFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean adapt$spawning(Level level, Entity projectile, Operation<Boolean> original) {
        return !EntityHooks.shotCancelled() && original.call(level, projectile);
    }
}
