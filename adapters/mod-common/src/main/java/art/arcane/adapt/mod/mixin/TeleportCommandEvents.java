package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.entity.TeleportCause;
import net.minecraft.server.commands.TeleportCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Teleports run by /tp and /teleport carry the command cause. */
@Mixin(TeleportCommand.class)
public abstract class TeleportCommandEvents {
    @Inject(method = "performTeleport", at = @At("HEAD"))
    private static void adapt$teleporting(CallbackInfo callback) {
        PlayerHooks.teleportCause(TeleportCause.COMMAND);
    }

    @Inject(method = "performTeleport", at = @At("RETURN"))
    private static void adapt$teleported(CallbackInfo callback) {
        PlayerHooks.teleportCauseEnd();
    }
}
