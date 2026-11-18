package art.arcane.adapt.gameplay.forge;

import art.arcane.adapt.gameplay.mod.ModFixtureCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("adaptqa")
public final class AdaptGameplayFixtureForge {
    private final ModFixtureCommand command = new ModFixtureCommand("forge");

    public AdaptGameplayFixtureForge(FMLJavaModLoadingContext context) {
        RegisterCommandsEvent.BUS.addListener(event -> command.register(event.getDispatcher()));
        ServerStoppingEvent.BUS.addListener(Priority.HIGH, event -> command.serverStopping(event.getServer()));
    }
}
