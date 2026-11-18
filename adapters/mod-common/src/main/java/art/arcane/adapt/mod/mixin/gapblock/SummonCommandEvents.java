package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.world.WorldHooks;
import art.arcane.adapt.platform.event.world.LightningStrikeEvent;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lightning summoned with /summon. */
@Mixin(SummonCommand.class)
public abstract class SummonCommandEvents {
    @Inject(method = "createEntity", at = @At("HEAD"))
    private static void adapt$summoning(CallbackInfoReturnable<Entity> callback) {
        WorldHooks.lightningCause(LightningStrikeEvent.Cause.COMMAND);
    }

    @Inject(method = "createEntity", at = @At("RETURN"))
    private static void adapt$summoned(CallbackInfoReturnable<Entity> callback) {
        WorldHooks.lightningCause(null);
    }
}
