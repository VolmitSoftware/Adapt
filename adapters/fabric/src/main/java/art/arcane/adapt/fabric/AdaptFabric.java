package art.arcane.adapt.fabric;

import art.arcane.adapt.mod.AdaptModBootstrap;
import art.arcane.adapt.mod.ModLoader;
import art.arcane.adapt.mod.feature.loader.LoaderRecipeSync;
import art.arcane.adapt.mod.platform.ModPermissionHost;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class AdaptFabric implements ModInitializer {
    private final AdaptModBootstrap bootstrap = new AdaptModBootstrap(ModLoader.FABRIC, FabricLoader.getInstance().getModContainer("fabricloader")
            .map((ModContainer container) -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown"), FabricLoader.getInstance().getConfigDir(), (String id) -> FabricLoader.getInstance().isModLoaded(id));

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().isModLoaded(FabricPermissions.MOD_ID)) {
            FabricPermissions permissions = FabricPermissions.create();
            if (permissions != null) {
                ModPermissionHost.bridge(permissions);
            }
        }
        LoaderRecipeSync.install();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> bootstrap.registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(bootstrap::serverStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(bootstrap::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(bootstrap::serverStopping);
    }
}
