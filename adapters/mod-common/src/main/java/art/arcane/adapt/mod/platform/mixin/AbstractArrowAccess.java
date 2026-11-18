package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccess {
    @Accessor("baseDamage")
    double adapt$baseDamage();

    @Invoker("setPierceLevel")
    void adapt$setPierceLevel(byte level);

    @Invoker("isInGround")
    boolean adapt$isInGround();
}
