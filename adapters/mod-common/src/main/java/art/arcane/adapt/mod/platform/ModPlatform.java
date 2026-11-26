package art.arcane.adapt.mod.platform;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.api.value.RegistryConfigBinding;
import art.arcane.adapt.mod.AdaptModIdentity;
import art.arcane.adapt.mod.platform.schedule.ModScheduler;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformFamily;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.permission.PermissionHost;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Process-wide access to the running modded platform. Everything is null outside a server lifetime
 * except {@link #bus()} and {@link #log()}, which live for the whole process.
 */
public final class ModPlatform {
    private static final ModLog LOG = new ModLog(AdaptModIdentity.NAME);
    private static final AdaptEventBus BUS = new AdaptEventBus((Object owner, AdaptEvent event, Throwable error) ->
            LOG.error("Event handler " + owner + " failed on " + event.getClass().getSimpleName(), error));
    private static final int PLAYER_SYNC_TICKS = 20;

    private static volatile MinecraftServer server;
    private static volatile ModScheduler scheduler;
    private static volatile ModPlatformHost host;
    private static volatile ModPermissionHost permissions;
    private static volatile AdaptPlatform platform;

    private ModPlatform() {
    }

    /** The running server, or null outside a server lifetime. */
    public static MinecraftServer server() {
        return server;
    }

    /** The assembled platform, or null before server start or while a registered host is missing. */
    public static AdaptPlatform platform() {
        return platform;
    }

    public static AdaptEventBus bus() {
        return BUS;
    }

    public static ModLog log() {
        return LOG;
    }

    public static AdaptScheduler scheduler() {
        return scheduler;
    }

    public static ModPlatformHost host() {
        return host;
    }

    public static PermissionHost permissions() {
        return permissions;
    }

    /** Server thread, while the server is starting. */
    public static void start(MinecraftServer running, PlatformFamily family, String platformName, Path configDirectory, Predicate<String> modPresent) {
        if (server != null) {
            stop(server);
        }
        ModScheduler created = new ModScheduler(LOG);
        created.bind(Thread.currentThread());
        server = running;
        scheduler = created;
        permissions = new ModPermissionHost();
        Path dataDirectory = configDirectory.resolve(AdaptModIdentity.ID);
        AdaptEnvironment.install(LOG, () -> dataDirectory);
        host = new ModPlatformHost(running, family, platformName, dataDirectory, LOG, modPresent);
        AdaptEnvironment.bindConfig(new RegistryConfigBinding(host.registries()));
        platform = assemble();
    }

    /** Server thread, once the server finished starting. */
    public static void started(MinecraftServer running) {
        notify(ModHosts.startedListeners(), running);
    }

    /** Server thread, at the end of every server tick. */
    public static void tick(MinecraftServer running) {
        ModScheduler current = scheduler;
        if (current == null || running != server) {
            return;
        }
        if (running.getTickCount() % PLAYER_SYNC_TICKS == 0 || ModRefs.cachedPlayers().size() != running.getPlayerList().getPlayerCount()) {
            ModRefs.syncPlayers(running.getPlayerList().getPlayers());
        }
        current.tick();
        for (Consumer<MinecraftServer> listener : ModHosts.tickListeners()) {
            try {
                listener.accept(running);
            } catch (RuntimeException failure) {
                LOG.error("Tick listener failed", failure);
            }
        }
    }

    /** Server thread, while the server is stopping. */
    public static void stop(MinecraftServer running) {
        if (running != server) {
            return;
        }
        notify(ModHosts.stoppingListeners(), running);
        ModScheduler current = scheduler;
        if (current != null) {
            current.shutdown();
        }
        platform = null;
        host = null;
        permissions = null;
        scheduler = null;
        server = null;
        ModRefs.clear();
    }

    private static AdaptPlatform assemble() {
        List<String> missing = new ArrayList<>();
        if (ModHosts.fx() == null) {
            missing.add("fx");
        }
        if (ModHosts.viewers() == null) {
            missing.add("viewers");
        }
        if (ModHosts.menus() == null) {
            missing.add("menus");
        }
        if (ModHosts.protection() == null) {
            missing.add("protection");
        }
        if (ModHosts.recipes() == null) {
            missing.add("recipes");
        }
        if (ModHosts.advancements() == null) {
            missing.add("advancements");
        }
        if (!missing.isEmpty()) {
            LOG.warn("Platform incomplete, missing hosts: " + String.join(", ", missing));
            return null;
        }
        return new AdaptPlatform(host, scheduler, ModHosts.fx(), ModHosts.viewers(), ModHosts.menus(), permissions,
                ModHosts.protection(), ModHosts.recipes(), ModHosts.advancements());
    }

    private static void notify(List<Consumer<MinecraftServer>> listeners, MinecraftServer running) {
        for (Consumer<MinecraftServer> listener : listeners) {
            try {
                listener.accept(running);
            } catch (RuntimeException failure) {
                LOG.error("Lifecycle listener failed", failure);
            }
        }
    }
}
