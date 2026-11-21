package art.arcane.adapt.neoforge;

import art.arcane.adapt.mod.AdaptModBootstrap;
import art.arcane.adapt.mod.AdaptModIdentity;
import art.arcane.adapt.mod.ModLoader;
import art.arcane.adapt.mod.feature.loader.LoaderRecipeSync;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@Mod(AdaptModIdentity.ID)
public final class AdaptNeoForge {
    private final AdaptModBootstrap bootstrap = new AdaptModBootstrap(ModLoader.NEOFORGE, ModList.get().getModContainerById("neoforge")
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("unknown"), FMLPaths.CONFIGDIR.get(), (String id) -> ModList.get().isLoaded(id));

    private final NeoForgePermissions permissions = new NeoForgePermissions(FMLPaths.CONFIGDIR.get());

    public AdaptNeoForge(IEventBus modBus) {
        LoaderRecipeSync.install();
        NeoForgeUiChannel.register(modBus);
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) -> permissions.gather(event));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> bootstrap.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> bootstrap.serverStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> bootstrap.serverStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerStartedEvent event) -> permissions.remember());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> bootstrap.serverStopping(event.getServer()));
    }
}
