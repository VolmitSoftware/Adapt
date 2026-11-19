package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptSunBurn;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobSunBurnMixin implements AdaptSunBurn {
    @Unique
    private static final String adapt$NO_SUN_BURN = "AdaptNoSunBurn";
    @Unique
    private boolean adapt$noSunBurn;

    @Inject(method = "isSunBurnTick()Z", at = @At("HEAD"), cancellable = true)
    private void adapt$sunBurn(CallbackInfoReturnable<Boolean> callback) {
        if (adapt$noSunBurn) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void adapt$saveSunBurn(CompoundTag tag, CallbackInfo callback) {
        if (adapt$noSunBurn) {
            tag.putBoolean(adapt$NO_SUN_BURN, true);
        }
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void adapt$loadSunBurn(CompoundTag tag, CallbackInfo callback) {
        adapt$noSunBurn = tag.getBoolean(adapt$NO_SUN_BURN);
    }

    @Override
    public boolean adapt$burnsInDaylight() {
        return !adapt$noSunBurn;
    }

    @Override
    public void adapt$setBurnsInDaylight(boolean burns) {
        adapt$noSunBurn = !burns;
    }
}
