package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Arrow.class)
public interface ArrowAccess {
    @Invoker("setPotionContents")
    void adapt$setPotionContents(PotionContents contents);
}
