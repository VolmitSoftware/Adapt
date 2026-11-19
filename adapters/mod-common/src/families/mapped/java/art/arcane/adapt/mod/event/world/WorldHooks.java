package art.arcane.adapt.mod.event.world;

import art.arcane.adapt.mod.AdaptModIdentity;
import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModHosts;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.server.PluginStateEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.event.world.LightningStrikeEvent;
import art.arcane.adapt.platform.event.world.WorldLifecycleEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Chunk, entity-section, world, weather and server lifecycle events. Plugin state maps to Adapt
 * itself: enabled once the server started, disabled when it stops. Service registrations have no
 * modded counterpart and are never posted.
 */
public final class WorldHooks {
    private static boolean installed;
    private static boolean commandReload;
    private static LightningStrikeEvent.Cause lightningCause;

    private WorldHooks() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        ModHosts.onServerStarted((MinecraftServer server) -> ModEvents.post(new PluginStateEvent(AdaptModIdentity.NAME, true)));
        ModHosts.onServerStopping((MinecraftServer server) -> ModEvents.post(new PluginStateEvent(AdaptModIdentity.NAME, false)));
    }

    /** LevelChunk#registerTickContainerInLevel TAIL: the chunk is in the level and ticking. */
    public static void chunkLoaded(ServerLevel level, LevelChunk chunk, boolean newChunk) {
        if (!ModEvents.listening(ChunkEvent.class)) {
            return;
        }
        ModEvents.post(new ChunkEvent(ModRefs.world(level), new ChunkPos(chunk.getPos().x, chunk.getPos().z), ChunkEvent.Phase.LOAD, newChunk));
    }

    /** ServerLevel#unload HEAD. */
    public static void chunkUnloading(ServerLevel level, LevelChunk chunk) {
        if (!ModEvents.listening(ChunkEvent.class)) {
            return;
        }
        ModEvents.post(new ChunkEvent(ModRefs.world(level), new ChunkPos(chunk.getPos().x, chunk.getPos().z), ChunkEvent.Phase.UNLOAD, false));
    }

    public static boolean listeningEntities() {
        return ModEvents.listening(ChunkEntitiesEvent.class);
    }

    /**
     * Entities of one chunk were added to, or are about to leave, a level's persistent entity
     * manager. The level comes from the entities; an empty chunk posts nothing.
     */
    public static void entities(List<?> loaded, int chunkX, int chunkZ, boolean load) {
        if (loaded.isEmpty() || !ModEvents.listening(ChunkEntitiesEvent.class)) {
            return;
        }
        ServerLevel level = null;
        List<EntityRef> entities = new ArrayList<>(loaded.size());
        for (Object candidate : loaded) {
            if (candidate instanceof Entity entity && entity.level() instanceof ServerLevel entityLevel) {
                level = entityLevel;
                entities.add(ModRefs.entity(entity));
            }
        }
        if (level == null) {
            return;
        }
        ModEvents.post(new ChunkEntitiesEvent(ModRefs.world(level), new ChunkPos(chunkX, chunkZ),
            load ? ChunkEntitiesEvent.Phase.LOAD : ChunkEntitiesEvent.Phase.UNLOAD, entities));
    }

    /** MinecraftServer#createLevels TAIL. */
    public static void levelsCreated(MinecraftServer server) {
        if (!ModEvents.listening(WorldLifecycleEvent.class)) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            ModEvents.post(new WorldLifecycleEvent(ModRefs.world(level), WorldLifecycleEvent.Phase.LOAD));
        }
    }

    public static void saving(ServerLevel level, boolean noSave) {
        if (noSave || !ModEvents.listening(WorldLifecycleEvent.class)) {
            return;
        }
        ModEvents.post(new WorldLifecycleEvent(ModRefs.world(level), WorldLifecycleEvent.Phase.SAVE));
    }

    /** MinecraftServer#stopServer, before each level closes. Vanilla cannot keep a level loaded, so a cancel is ignored. */
    public static void closing(ServerLevel level) {
        if (!ModEvents.listening(WorldLifecycleEvent.class)) {
            return;
        }
        ModEvents.post(new WorldLifecycleEvent(ModRefs.world(level), WorldLifecycleEvent.Phase.UNLOAD));
    }

    public static void lightningCause(LightningStrikeEvent.Cause cause) {
        lightningCause = cause;
    }

    /** ServerLevel#addFreshEntity HEAD for lightning bolts. False when a handler cancelled the strike. */
    public static boolean striking(ServerLevel level, Entity entity) {
        if (!(entity instanceof LightningBolt) || !ModEvents.listening(LightningStrikeEvent.class)) {
            return true;
        }
        LightningStrikeEvent.Cause cause = lightningCause == null ? LightningStrikeEvent.Cause.UNKNOWN : lightningCause;
        lightningCause = null;
        LightningStrikeEvent event = ModEvents.post(new LightningStrikeEvent(ModRefs.world(level), ModRefs.entity(entity),
            ModRefs.loc(level, entity.position(), entity.getYRot(), entity.getXRot()), cause));
        return !event.isCancelled();
    }

    public static void commandReload(boolean active) {
        commandReload = active;
    }

    /** MinecraftServer#reloadResources RETURN: posts once the new resources are in place, on the server thread. */
    public static CompletableFuture<Void> reloading(CompletableFuture<Void> future) {
        ResourcesReloadedEvent.Cause cause = commandReload ? ResourcesReloadedEvent.Cause.COMMAND : ResourcesReloadedEvent.Cause.PLUGIN;
        commandReload = false;
        return future.thenRun(() -> ModEvents.post(new ResourcesReloadedEvent(cause)));
    }
}
