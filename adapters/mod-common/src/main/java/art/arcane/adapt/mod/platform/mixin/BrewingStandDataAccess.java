package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandDataAccess {
    @Accessor("dataAccess")
    ContainerData adapt$dataAccess();
}
