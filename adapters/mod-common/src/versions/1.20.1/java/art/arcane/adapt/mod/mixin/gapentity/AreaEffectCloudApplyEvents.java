package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.PotionHooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.Potion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;

/** One application round of a lingering cloud. */
@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudApplyEvents {
    @Shadow
    private Potion potion;

    @Shadow
    @Final
    private List<MobEffectInstance> effects;

    @Shadow
    @Final
    private Map<Entity, Integer> victims;

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<LivingEntity> adapt$cloudApplying(List<LivingEntity> candidates) {
        return PotionHooks.cloudApplying((AreaEffectCloud) (Object) this, potion, effects, victims, candidates);
    }
}
