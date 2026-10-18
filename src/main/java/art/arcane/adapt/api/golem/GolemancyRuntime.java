package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.minion.MinionKind;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class GolemancyRuntime {
  public static final MinionKind MINION_KIND = new BoundGolemKind();

  private static volatile GolemRegistry registry;
  private static volatile GolemSettings settings = GolemSettings.defaults();

  private GolemancyRuntime() {
  }

  public static synchronized void startRuntime() {
    MinionIdentity.registerKind(MINION_KIND);
    if (registry != null) {
      return;
    }
    GolemRegistry created = new GolemRegistry(new BukkitGolemPlatform(), GolemancyRuntime::settings);
    created.activateRuntime();
    registry = created;
    adoptLoaded(created);
    created.joinOnlineOwners();
  }

  public static boolean shutdown(long timeoutMillis) {
    GolemRegistry local;
    synchronized (GolemancyRuntime.class) {
      local = registry;
      registry = null;
    }
    if (local == null) {
      return true;
    }
    boolean flushed;
    try {
      flushed = local.flushAll(timeoutMillis);
    } catch (RuntimeException failure) {
      Adapt.warn("Bound golem shutdown flush failed.", failure);
      flushed = false;
    }
    local.unregister();
    return flushed;
  }

  public static Optional<GolemRegistry> registry() {
    return Optional.ofNullable(registry);
  }

  public static Optional<GolemHandle> handle(Entity entity) {
    GolemRegistry local = registry;
    return local == null ? Optional.empty() : local.handle(entity);
  }

  public static GolemSettings settings() {
    return settings;
  }

  public static void configure(GolemSettings next) {
    GolemSettings previous = settings;
    settings = Objects.requireNonNull(next);
    GolemRegistry local = registry;
    if (local != null && next.serverMaxActiveGolems() < previous.serverMaxActiveGolems()) {
      local.enforceCap();
    }
  }

  private static void adoptLoaded(GolemRegistry target) {
    if (J.isFoliaThreading()) {
      return;
    }
    for (World world : Bukkit.getWorlds()) {
      for (Mob mob : world.getEntitiesByClass(Mob.class)) {
        if (GolemState.has(mob)) {
          target.adopt(mob);
        }
      }
    }
  }

  private static final class BoundGolemKind implements MinionKind {
    @Override
    public boolean matches(Entity entity) {
      return GolemState.has(entity);
    }

    @Override
    public UUID ownerOf(Entity entity) {
      GolemRegistry local = registry;
      Optional<UUID> owner = local == null ? GolemState.read(entity).map(GolemState::owner) : local.ownerOf(entity);
      return owner.orElse(null);
    }
  }

  private static final class BukkitGolemPlatform implements GolemPlatform {
    @Override
    public boolean isOnline(UUID owner) {
      return Bukkit.getPlayer(owner) != null;
    }

    @Override
    public Collection<UUID> onlineOwners() {
      Collection<? extends Player> players = Bukkit.getOnlinePlayers();
      List<UUID> owners = new ArrayList<>(players.size());
      for (Player player : players) {
        owners.add(player.getUniqueId());
      }
      return owners;
    }

    @Override
    public Optional<GolemRoster> roster(UUID owner) {
      AdaptPlayer player = onlinePlayer(owner);
      return player == null ? Optional.empty() : Optional.of(player.getData().getGolemancy());
    }

    @Override
    public void rosterChanged(UUID owner) {
      AdaptPlayer player = onlinePlayer(owner);
      if (player != null) {
        player.requestSave();
      }
    }

    @Override
    public boolean owns(Entity entity) {
      return J.isOwnedByCurrentRegion(entity);
    }

    @Override
    public boolean runEntity(Entity entity, Runnable task, int delayTicks) {
      return delayTicks <= 0 ? J.runEntity(entity, task) : J.runEntity(entity, task, delayTicks);
    }

    @Override
    public boolean isChunkLoaded(UUID world, long chunkKey) {
      World loaded = Bukkit.getWorld(world);
      return loaded != null && loaded.isChunkLoaded(GolemRoster.chunkX(chunkKey), GolemRoster.chunkZ(chunkKey));
    }

    @Override
    public void sweepChunk(UUID worldId, long chunkKey, Consumer<Mob> boundGolem) {
      World world = Bukkit.getWorld(worldId);
      int chunkX = GolemRoster.chunkX(chunkKey);
      int chunkZ = GolemRoster.chunkZ(chunkKey);
      if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
        return;
      }
      Location center = new Location(world, (chunkX << 4) + 8D, world.getMinHeight(), (chunkZ << 4) + 8D);
      J.runAt(center, () -> {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return;
        }
        for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
          if (entity instanceof Mob mob && GolemState.has(mob)) {
            boundGolem.accept(mob);
          }
        }
      });
    }

    @Override
    public AttributeInstance scaleOf(LivingEntity entity) {
      return GolemScale.scaleOf(entity);
    }

    @Override
    public long nowMillis() {
      return System.currentTimeMillis();
    }

    private static AdaptPlayer onlinePlayer(UUID owner) {
      Adapt plugin = Adapt.instance;
      AdaptServer server = plugin == null ? null : plugin.getAdaptServer();
      return server == null ? null : server.getOnlineAdaptPlayer(owner);
    }
  }
}
