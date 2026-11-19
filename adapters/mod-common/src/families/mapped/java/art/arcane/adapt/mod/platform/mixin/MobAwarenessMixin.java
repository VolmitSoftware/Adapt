package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptMobAwareness;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
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

    @Inject(method = "serverAiStep()V", at = @At("HEAD"), cancellable = true)
    private void adapt$skipAi(CallbackInfo callback) {
        if (adapt$unaware) {
            callback.cancel();
        }
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void adapt$saveAwareness(CompoundTag tag, CallbackInfo callback) {
        if (adapt$unaware) {
            tag.putBoolean(adapt$UNAWARE, true);
        }
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void adapt$loadAwareness(CompoundTag tag, CallbackInfo callback) {
        adapt$unaware = tag.getBoolean(adapt$UNAWARE);
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
