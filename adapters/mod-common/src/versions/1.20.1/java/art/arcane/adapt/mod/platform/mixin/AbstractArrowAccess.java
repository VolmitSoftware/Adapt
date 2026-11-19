package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccess {
    @Accessor("baseDamage")
    double adapt$baseDamage();

    @Invoker("setPierceLevel")
    void adapt$setPierceLevel(byte level);

    @Accessor("inGround")
    boolean adapt$isInGround();

    @Invoker("getPickupItem")
    ItemStack adapt$pickupItem();
}
