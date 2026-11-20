package art.arcane.adapt.gameplay.neoforge;

import art.arcane.adapt.gameplay.mod.ModFixtureCommand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod("adaptqa")
public final class AdaptGameplayFixtureNeoForge {
    private final ModFixtureCommand command = new ModFixtureCommand("neoforge");
    private final RecipeSyncProbe recipeSync = new RecipeSyncProbe();

    public AdaptGameplayFixtureNeoForge() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> command.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> recipeSync.register(event.getDispatcher()));
        recipeSync.install();
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (ServerStoppingEvent event) -> command.serverStopping(event.getServer()));
    }
}
