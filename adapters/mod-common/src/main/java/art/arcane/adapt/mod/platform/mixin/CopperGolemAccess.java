package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CopperGolem.class)
public interface CopperGolemAccess {
    @Accessor("nextWeatheringTick")
    long adapt$nextWeatheringTick();

    @Accessor("nextWeatheringTick")
    void adapt$setNextWeatheringTick(long tick);
}
