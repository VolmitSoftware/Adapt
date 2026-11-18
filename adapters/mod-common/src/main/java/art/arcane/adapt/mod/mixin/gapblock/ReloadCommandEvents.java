package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.world.WorldHooks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ReloadCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** Marks the next resource reload as started by /reload or /datapack. */
@Mixin(ReloadCommand.class)
public abstract class ReloadCommandEvents {
    @Inject(method = "reloadPacks", at = @At("HEAD"))
    private static void adapt$reloading(Collection<String> packs, CommandSourceStack source, CallbackInfo callback) {
        WorldHooks.commandReload(true);
    }
}
