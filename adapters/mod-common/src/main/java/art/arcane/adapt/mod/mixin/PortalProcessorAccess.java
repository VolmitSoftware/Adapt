package art.arcane.adapt.mod.mixin;

import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PortalProcessor.class)
public interface PortalProcessorAccess {
    @Accessor("portal")
    Portal adapt$portal();
}
