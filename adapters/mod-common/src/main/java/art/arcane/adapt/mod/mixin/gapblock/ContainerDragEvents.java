package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** The last packet of a drag over two or more slots, before vanilla spreads the carried stack. */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerDragEvents {
    @Shadow
    private int quickcraftType;

    @Shadow
    private int quickcraftStatus;

    @Shadow
    @Final
    private Set<Slot> quickcraftSlots;

    @Shadow
    protected abstract void resetQuickCraft();

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void adapt$dragging(int slotIndex, int buttonNum, ContainerInput input, Player player, CallbackInfo callback) {
        if (input != ContainerInput.QUICK_CRAFT || quickcraftStatus != 1 || AbstractContainerMenu.getQuickcraftHeader(buttonNum) != 2
            || quickcraftSlots.size() < 2) {
            return;
        }
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!ContainerHooks.dragging(menu, player, quickcraftSlots, quickcraftType)) {
            resetQuickCraft();
            menu.sendAllDataToRemote();
            callback.cancel();
        }
    }
}
