package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The anvil result and cost, after vanilla (and the loader's anvil event) computed them. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuEvents {
    @Shadow
    @Final
    private DataSlot cost;

    @Shadow
    private String itemName;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void adapt$prepared(CallbackInfo callback) {
        ContainerHooks.preparedAnvil((AbstractContainerMenu) (Object) this, itemName, cost.get(), cost::set);
    }
}
