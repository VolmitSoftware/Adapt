package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptMobAwareness;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobAwarenessMixin implements AdaptMobAwareness {
    @Unique
    private static final String adapt$UNAWARE = "AdaptUnaware";
    @Unique
    private boolean adapt$unaware;

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void adapt$skipAi(CallbackInfo callback) {
        if (adapt$unaware) {
            callback.cancel();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void adapt$saveAwareness(ValueOutput output, CallbackInfo callback) {
        if (adapt$unaware) {
            output.putBoolean(adapt$UNAWARE, true);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void adapt$loadAwareness(ValueInput input, CallbackInfo callback) {
        adapt$unaware = input.getBooleanOr(adapt$UNAWARE, false);
    }

    @Override
    public boolean adapt$isAware() {
        return !adapt$unaware;
    }

    @Override
    public void adapt$setAware(boolean aware) {
        adapt$unaware = !aware;
    }
}
