package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.PotionHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** A splash potion bursting: the splash event sees vanilla's candidates and can stop every effect. */
@Mixin(ThrownSplashPotion.class)
public abstract class SplashPotionEvents {
    @ModifyExpressionValue(method = "onHitAsPotion", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<LivingEntity> adapt$splashing(List<LivingEntity> candidates, @Local(argsOnly = true) ItemStack potionItem,
                                               @Local(argsOnly = true) HitResult hit) {
        return PotionHooks.splashing((AbstractThrownPotion) (Object) this, potionItem, hit, candidates);
    }
}
