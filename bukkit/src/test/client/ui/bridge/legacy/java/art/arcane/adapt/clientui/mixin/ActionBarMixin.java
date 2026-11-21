package art.arcane.adapt.clientui.mixin;

import art.arcane.adapt.clientui.Recorder;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class ActionBarMixin {
    @Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
    private void adaptUiRecordActionBar(Component message, boolean animate, CallbackInfo callback) {
        Recorder.actionBar(message, animate);
    }
}
