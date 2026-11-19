package art.arcane.adapt.gameplay.forge;

import art.arcane.adapt.gameplay.mod.ModFixtureCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod("adaptqa")
public final class AdaptGameplayFixtureForge {
    private final ModFixtureCommand command = new ModFixtureCommand("forge");

    public AdaptGameplayFixtureForge() {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> command.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, ServerStoppingEvent.class,
                (ServerStoppingEvent event) -> command.serverStopping(event.getServer()));
    }
}
