package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The grindstone result, after vanilla set it. */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneResultEvents {
    @Inject(method = "createResult()V", at = @At("RETURN"))
    private void adapt$prepared(CallbackInfo callback) {
        ContainerHooks.preparedResult((AbstractContainerMenu) (Object) this);
    }
}
