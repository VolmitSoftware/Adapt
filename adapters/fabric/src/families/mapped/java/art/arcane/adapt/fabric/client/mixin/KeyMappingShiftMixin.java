package art.arcane.adapt.fabric.client.mixin;

import art.arcane.adapt.fabric.client.ShiftKeyMapping;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Routes Shift presses to {@link ShiftKeyMapping}s at their default and keeps those out of the vanilla key table, which
 * holds one mapping per key on these versions.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingShiftMixin {
    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void adapt$claimShiftPress(InputConstants.Key key, CallbackInfo callback) {
        if (ShiftKeyMapping.claim(key)) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object adapt$keepNewChordOutOfTable(Map<Object, Object> map, Object key, Object value, Operation<Object> put) {
        return keepOut(key, value) ? null : put.call(map, key, value);
    }

    @WrapOperation(method = "resetMapping", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object adapt$keepChordOutOfTable(Map<Object, Object> map, Object key, Object value, Operation<Object> put) {
        return keepOut(key, value) ? null : put.call(map, key, value);
    }

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void adapt$chordDiffersFromPlainKey(KeyMapping other, CallbackInfoReturnable<Boolean> callback) {
        if (ShiftKeyMapping.chorded((KeyMapping) (Object) this) != ShiftKeyMapping.chorded(other)) {
            callback.setReturnValue(false);
        }
    }

    private static boolean keepOut(Object key, Object value) {
        return key instanceof InputConstants.Key bound && value instanceof KeyMapping mapping && !ShiftKeyMapping.keyTable(mapping, bound);
    }
}
