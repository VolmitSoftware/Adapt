package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The orb value is a plain field here: clients see a new value only when the orb is sent to them again. */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccess {
    @Accessor("count")
    int adapt$count();

    @Accessor("count")
    void adapt$setCount(int count);

    @Accessor("value")
    void adapt$setValue(int value);
}
