package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerLootGenerateEvent;
import art.arcane.adapt.platform.event.server.PluginStateEvent;
import art.arcane.adapt.platform.event.server.ServiceChangeEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.event.world.LightningStrikeEvent;
import art.arcane.adapt.platform.event.world.NightSkipEvent;
import art.arcane.adapt.platform.event.world.WorldLifecycleEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.compat.PaperApi;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/** World, chunk and server event translations. */
final class WorldTranslations {
  private WorldTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(CHUNK_LOAD, CHUNK_UNLOAD, ENTITIES_LOAD, ENTITIES_UNLOAD, WORLD_LOAD, WORLD_SAVE, WORLD_UNLOAD, LIGHTNING, NIGHT_SKIP, LOOT_GENERATE,
        PLUGIN_ENABLE, PLUGIN_DISABLE, SERVICE_REGISTER, SERVICE_UNREGISTER);
  }

  static final Translation<org.bukkit.event.world.TimeSkipEvent> NIGHT_SKIP = BukkitTranslations.simple(
      org.bukkit.event.world.TimeSkipEvent.class, NightSkipEvent.class, (event, poster) -> {
        if (event.isCancelled() || !PaperApi.nightSkip(event)) {
          return;
        }
        List<PlayerRef> sleepers = event.getWorld().getPlayers().stream()
            .filter(PaperApi::deeplySleeping)
            .map(BukkitRefs::player)
            .toList();
        poster.post(new NightSkipEvent(BukkitRefs.world(event.getWorld()), sleepers));
      });

  static final Translation<org.bukkit.event.world.LootGenerateEvent> LOOT_GENERATE = BukkitTranslations.simple(
      org.bukkit.event.world.LootGenerateEvent.class, PlayerLootGenerateEvent.class, (event, poster) -> {
        Location origin = event.getLootContext().getLocation();
        if (event.isCancelled() || event.isPlugin() || !(event.getEntity() instanceof Player player)
            || event.getInventoryHolder() == null || origin.getWorld() == null) {
          return;
        }
        poster.post(new PlayerLootGenerateEvent(BukkitRefs.player(player), BukkitRefs.loc(origin)));
      });

  static final Translation<org.bukkit.event.world.ChunkLoadEvent> CHUNK_LOAD = BukkitTranslations.simple(
      org.bukkit.event.world.ChunkLoadEvent.class, ChunkEvent.class,
      (event, poster) -> poster.post(chunk(event.getChunk(), ChunkEvent.Phase.LOAD, event.isNewChunk())));

  static final Translation<org.bukkit.event.world.ChunkUnloadEvent> CHUNK_UNLOAD = BukkitTranslations.simple(
      org.bukkit.event.world.ChunkUnloadEvent.class, ChunkEvent.class,
      (event, poster) -> poster.post(chunk(event.getChunk(), ChunkEvent.Phase.UNLOAD, false)));

  static final Translation<org.bukkit.event.world.EntitiesLoadEvent> ENTITIES_LOAD = BukkitTranslations.simple(
      org.bukkit.event.world.EntitiesLoadEvent.class, ChunkEntitiesEvent.class, (event, poster) -> {
        Chunk chunk = event.getChunk();
        poster.post(new ChunkEntitiesEvent(BukkitRefs.world(chunk.getWorld()), new ChunkPos(chunk.getX(), chunk.getZ()),
            ChunkEntitiesEvent.Phase.LOAD, GapEventMaps.entities(event.getEntities())));
      });

  static final Translation<org.bukkit.event.world.EntitiesUnloadEvent> ENTITIES_UNLOAD = BukkitTranslations.simple(
      org.bukkit.event.world.EntitiesUnloadEvent.class, ChunkEntitiesEvent.class, (event, poster) -> {
        Chunk chunk = event.getChunk();
        poster.post(new ChunkEntitiesEvent(BukkitRefs.world(chunk.getWorld()), new ChunkPos(chunk.getX(), chunk.getZ()),
            ChunkEntitiesEvent.Phase.UNLOAD, GapEventMaps.entities(event.getEntities())));
      });

  static final Translation<org.bukkit.event.world.WorldLoadEvent> WORLD_LOAD = BukkitTranslations.simple(
      org.bukkit.event.world.WorldLoadEvent.class, WorldLifecycleEvent.class,
      (event, poster) -> poster.post(new WorldLifecycleEvent(BukkitRefs.world(event.getWorld()), WorldLifecycleEvent.Phase.LOAD)));

  static final Translation<org.bukkit.event.world.WorldSaveEvent> WORLD_SAVE = BukkitTranslations.simple(
      org.bukkit.event.world.WorldSaveEvent.class, WorldLifecycleEvent.class,
      (event, poster) -> poster.post(new WorldLifecycleEvent(BukkitRefs.world(event.getWorld()), WorldLifecycleEvent.Phase.SAVE)));

  static final Translation<org.bukkit.event.world.WorldUnloadEvent> WORLD_UNLOAD = BukkitTranslations.simple(
      org.bukkit.event.world.WorldUnloadEvent.class, WorldLifecycleEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        WorldLifecycleEvent core = new WorldLifecycleEvent(BukkitRefs.world(event.getWorld()), WorldLifecycleEvent.Phase.UNLOAD);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.weather.LightningStrikeEvent> LIGHTNING = BukkitTranslations.simple(
      org.bukkit.event.weather.LightningStrikeEvent.class, LightningStrikeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        LightningStrike lightning = event.getLightning();
        LightningStrikeEvent core = new LightningStrikeEvent(BukkitRefs.world(event.getWorld()), entity(lightning),
            BukkitRefs.loc(lightning.getLocation()), GapEventMaps.lightningCause(event.getCause()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.server.PluginEnableEvent> PLUGIN_ENABLE = BukkitTranslations.simple(
      org.bukkit.event.server.PluginEnableEvent.class, PluginStateEvent.class,
      (event, poster) -> poster.post(new PluginStateEvent(event.getPlugin().getName(), true)));

  static final Translation<org.bukkit.event.server.PluginDisableEvent> PLUGIN_DISABLE = BukkitTranslations.simple(
      org.bukkit.event.server.PluginDisableEvent.class, PluginStateEvent.class,
      (event, poster) -> poster.post(new PluginStateEvent(event.getPlugin().getName(), false)));

  static final Translation<org.bukkit.event.server.ServiceRegisterEvent> SERVICE_REGISTER = BukkitTranslations.simple(
      org.bukkit.event.server.ServiceRegisterEvent.class, ServiceChangeEvent.class,
      (event, poster) -> service(event.getProvider(), true, poster));

  static final Translation<org.bukkit.event.server.ServiceUnregisterEvent> SERVICE_UNREGISTER = BukkitTranslations.simple(
      org.bukkit.event.server.ServiceUnregisterEvent.class, ServiceChangeEvent.class,
      (event, poster) -> service(event.getProvider(), false, poster));

  private static ChunkEvent chunk(Chunk chunk, ChunkEvent.Phase phase, boolean newChunk) {
    World world = chunk.getWorld();
    WorldRef ref = BukkitRefs.world(world);
    return new ChunkEvent(ref, new ChunkPos(chunk.getX(), chunk.getZ()), phase, newChunk);
  }

  private static void service(RegisteredServiceProvider<?> provider, boolean registered, Translation.Poster poster) {
    if (provider == null || provider.getService() == null) {
      return;
    }
    poster.post(new ServiceChangeEvent(provider.getService(),
        provider.getPlugin() == null ? null : provider.getPlugin().getName(), registered));
  }
}
