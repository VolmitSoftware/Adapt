package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The smithing result, after vanilla set it. Each result menu has its own mixin on this version: one mixin over several
 * menus cannot hold their different obfuscated names.
 */
@Mixin(SmithingMenu.class)
public abstract class ResultMenuEvents {
    @Inject(method = "createResult()V", at = @At("RETURN"))
    private void adapt$prepared(CallbackInfo callback) {
        ContainerHooks.preparedResult((AbstractContainerMenu) (Object) this);
    }
}
