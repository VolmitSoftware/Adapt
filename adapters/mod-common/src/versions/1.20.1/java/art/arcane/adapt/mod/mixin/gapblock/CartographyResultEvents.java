package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cartography table result, after vanilla set it. */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyResultEvents {
    @Inject(method = "setupResultSlot(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void adapt$prepared(CallbackInfo callback) {
        ContainerHooks.preparedResult((AbstractContainerMenu) (Object) this);
    }
}
