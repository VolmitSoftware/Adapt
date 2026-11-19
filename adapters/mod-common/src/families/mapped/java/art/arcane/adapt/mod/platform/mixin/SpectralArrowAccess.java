package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.projectile.SpectralArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpectralArrow.class)
public interface SpectralArrowAccess {
    @Accessor("duration")
    int adapt$duration();

    @Accessor("duration")
    void adapt$setDuration(int duration);
}
