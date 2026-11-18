package art.arcane.adapt.clientqa.mixin;

import art.arcane.adapt.clientqa.ClientBridge;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Shadow @Final
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "play", at = @At("RETURN"))
    private void playedSound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        ClientBridge.sound(sound, callback.getReturnValue(),
                ((SoundEngine) (Object) this).isActive(sound), instanceToChannel.get(sound));
    }
}
