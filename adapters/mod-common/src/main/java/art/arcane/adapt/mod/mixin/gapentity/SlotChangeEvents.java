package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/** Changes to the player's inventory slots as menus synchronize them. */
@Mixin(AbstractContainerMenu.class)
public abstract class SlotChangeEvents {
    @Shadow
    @Final
    private NonNullList<ItemStack> lastSlots;

    @Inject(method = "triggerSlotListeners", at = @At("HEAD"))
    private void adapt$slotChanging(int index, ItemStack current, Supplier<ItemStack> currentCopy, CallbackInfo callback) {
        if (index < lastSlots.size()) {
            PlayerGapHooks.slotChanging((AbstractContainerMenu) (Object) this, index, lastSlots.get(index), current);
        }
    }
}
