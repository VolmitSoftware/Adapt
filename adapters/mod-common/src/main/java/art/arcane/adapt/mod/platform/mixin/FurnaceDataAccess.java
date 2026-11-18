package art.arcane.adapt.mod.platform.mixin;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceDataAccess {
    @Accessor("dataAccess")
    ContainerData adapt$dataAccess();
}
