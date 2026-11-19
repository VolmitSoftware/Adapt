package art.arcane.adapt.forge;

import art.arcane.adapt.mod.AdaptModBootstrap;
import art.arcane.adapt.mod.AdaptModIdentity;
import art.arcane.adapt.mod.ModLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.versions.forge.ForgeVersion;

@Mod(AdaptModIdentity.ID)
public final class AdaptForge {
    private final AdaptModBootstrap bootstrap = new AdaptModBootstrap(ModLoader.FORGE, ForgeVersion.getVersion(), FMLPaths.CONFIGDIR.get(),
            (String id) -> ModList.get().isLoaded(id));

    private final ForgePermissions permissions = new ForgePermissions(FMLPaths.CONFIGDIR.get());

    public AdaptForge() {
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((PermissionGatherEvent.Nodes event) -> permissions.gather(event));
        bus.addListener((RegisterCommandsEvent event) -> bootstrap.registerCommands(event.getDispatcher()));
        bus.addListener((ServerStartingEvent event) -> bootstrap.serverStarting(event.getServer()));
        bus.addListener((ServerStartedEvent event) -> bootstrap.serverStarted(event.getServer()));
        bus.addListener(EventPriority.LOWEST, false, ServerStartedEvent.class, (ServerStartedEvent event) -> permissions.remember());
        bus.addListener((ServerStoppingEvent event) -> bootstrap.serverStopping(event.getServer()));
    }
}
