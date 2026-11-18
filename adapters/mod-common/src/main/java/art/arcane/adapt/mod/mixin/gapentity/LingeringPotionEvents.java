package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.PotionHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A lingering potion bursting into its cloud. */
@Mixin(ThrownLingeringPotion.class)
public abstract class LingeringPotionEvents {
    @WrapOperation(method = "onHitAsPotion", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$lingering(ServerLevel level, Entity cloud, Operation<Boolean> original, @Local(argsOnly = true) ItemStack potionItem) {
        if (PotionHooks.lingering((AbstractThrownPotion) (Object) this, potionItem, cloud)) {
            return false;
        }
        return original.call(level, cloud);
    }
}
