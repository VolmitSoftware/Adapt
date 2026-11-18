package art.arcane.adapt.mod.mixin.gapblock;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandAccess {
    @Accessor("fuel")
    int adapt$fuel();

    @Accessor("brewTime")
    int adapt$brewTime();
}
