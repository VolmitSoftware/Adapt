package art.arcane.adapt.clientui.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int adaptUiLeftPos();

    @Accessor("topPos")
    int adaptUiTopPos();

    @Accessor("hoveredSlot")
    Slot adaptUiHoveredSlot();
}
