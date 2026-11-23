package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.SleepHooks;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla wakes every player only when enough of them slept the night away. */
@Mixin(ServerLevel.class)
public abstract class NightSkipEvents {
    @Inject(method = "wakeUpAllPlayers", at = @At("HEAD"))
    private void adapt$nightSkipped(CallbackInfo callback) {
        SleepHooks.nightSkipped((ServerLevel) (Object) this);
    }
}
