package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.PotionHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A lingering potion bursting into its cloud. */
@Mixin(ThrownPotion.class)
public abstract class LingeringPotionEvents {
    @WrapOperation(method = "makeAreaOfEffectCloud", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean adapt$lingering(Level level, Entity cloud, Operation<Boolean> original) {
        ThrownPotion potion = (ThrownPotion) (Object) this;
        if (PotionHooks.lingering(potion, potion.getItem(), cloud)) {
            return false;
        }
        return original.call(level, cloud);
    }
}
