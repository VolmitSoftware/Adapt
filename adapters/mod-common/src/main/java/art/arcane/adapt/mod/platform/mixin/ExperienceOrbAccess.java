package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccess {
    @Accessor("count")
    int adapt$count();

    @Accessor("count")
    void adapt$setCount(int count);

    @Invoker("setValue")
    void adapt$setValue(int value);
}
