package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccess {
    @Accessor("lastHurt")
    void adapt$setLastHurt(float lastHurt);
}
