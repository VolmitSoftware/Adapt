package art.arcane.adapt.fabric.client.mixin;

import art.arcane.adapt.fabric.client.ShiftKeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes Shift presses to {@link ShiftKeyMapping}s at their default and keeps those out of the vanilla key table. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingShiftMixin {
    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void adapt$claimShiftPress(InputConstants.Key key, CallbackInfo callback) {
        if (ShiftKeyMapping.claim(key)) {
            callback.cancel();
        }
    }

    @Inject(method = "registerMapping", at = @At("HEAD"), cancellable = true)
    private void adapt$keepChordOutOfTable(InputConstants.Key key, CallbackInfo callback) {
        if (!ShiftKeyMapping.keyTable((KeyMapping) (Object) this, key)) {
            callback.cancel();
        }
    }

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void adapt$chordDiffersFromPlainKey(KeyMapping other, CallbackInfoReturnable<Boolean> callback) {
        if (ShiftKeyMapping.chorded((KeyMapping) (Object) this) != ShiftKeyMapping.chorded(other)) {
            callback.setReturnValue(false);
        }
    }
}
