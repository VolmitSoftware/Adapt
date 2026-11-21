package art.arcane.adapt.forge;

import art.arcane.adapt.forge.client.AdaptForgeClient;
import art.arcane.adapt.mod.AdaptModBootstrap;
import art.arcane.adapt.mod.AdaptModIdentity;
import art.arcane.adapt.mod.ModLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.versions.forge.ForgeVersion;

@Mod(AdaptModIdentity.ID)
public final class AdaptForge {
    private final AdaptModBootstrap bootstrap = new AdaptModBootstrap(ModLoader.FORGE, ForgeVersion.getVersion(), FMLPaths.CONFIGDIR.get(),
            ModList::isLoaded);

    private final ForgePermissions permissions = new ForgePermissions(FMLPaths.CONFIGDIR.get());

    public AdaptForge(FMLJavaModLoadingContext context) {
        PermissionGatherEvent.Nodes.BUS.addListener((PermissionGatherEvent.Nodes event) -> permissions.gather(event));
        RegisterCommandsEvent.BUS.addListener(event -> bootstrap.registerCommands(event.getDispatcher()));
        ServerStartingEvent.BUS.addListener(event -> bootstrap.serverStarting(event.getServer()));
        ServerStartedEvent.BUS.addListener(event -> bootstrap.serverStarted(event.getServer()));
        ServerStartedEvent.BUS.addListener(Priority.LOWEST, (ServerStartedEvent event) -> permissions.remember());
        ServerStoppingEvent.BUS.addListener(event -> bootstrap.serverStopping(event.getServer()));
        ForgeUiChannel.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AdaptForgeClient.initialize();
        }
    }
}
