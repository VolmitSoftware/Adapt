package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.Gameplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class AttackGrabMixin {
    @Redirect(method = "handleKeybinds()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean adaptUiHeldAttack(MouseHandler handler) {
        return handler.isMouseGrabbed() || Gameplay.attacking();
    }
}
