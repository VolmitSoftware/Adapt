package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeSettings;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.golem.reveal.RevealSettings;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.minion.MinionKind;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class GolemancyRuntime {
  public static final MinionKind MINION_KIND = new BoundGolemKind();

  private static final String MOB_GOALS_CLASS = "com.destroystokyo.paper.entity.ai.MobGoals";
  private static final GolemTargetPolicy COMPANION = new CompanionTargetPolicy();
  private static final Map<GolemCoreType, GolemTargetPolicy> TARGET_POLICIES = new ConcurrentHashMap<>();
  private static volatile GolemRegistry registry;
  private static volatile GolemBrains brains;
  private static volatile GolemSettings settings = GolemSettings.defaults();
  private static volatile GolemBrainSettings brainSettings = GolemBrainSettings.defaults();
  private static volatile RevealService reveal;
  private static volatile RevealSettings revealSettings = RevealSettings.defaults();
  private static volatile NodeService nodes;
  private static volatile NodeSettings nodeSettings = NodeSettings.defaults();
  private static volatile NodeWeavingPolicy nodeWeaving;
  private static volatile Adaptation<?> protectionPolicy;
  private static volatile GolemBindListener bindListener = (owner, handle) -> {
  };

  private GolemancyRuntime() {
  }

  public static synchronized void startRuntime() {
    MinionIdentity.registerKind(MINION_KIND);
    if (registry != null) {
      return;
    }
    GolemPlatform platform = new BukkitGolemPlatform();
    GolemBrains createdBrains = new GolemBrains(new GolemBrain.Context(platform, GolemancyRuntime::brainSettings, GolemancyRuntime::targetPolicy),
        GolemancyRuntime::controllerFor);
    GolemRegistry created = new GolemRegistry(platform, GolemancyRuntime::settings, createdBrains);
    created.activateRuntime();
    brains = createdBrains;
    registry = created;
    adoptLoaded(created);
    created.joinOnlineOwners();
    RevealService createdReveal = new RevealService(GolemancyRuntime::revealSettings);
    createdReveal.activateRuntime();
    reveal = createdReveal;
    NodeService createdNodes = NodeService.create(GolemancyRuntime::nodeSettings, GolemancyRuntime::revealSettings);
    createdNodes.activateRuntime();
    nodes = createdNodes;
    if (!J.isFoliaThreading()) {
      createdNodes.indexLoadedChunks();
    }
  }

  public static boolean shutdown(long timeoutMillis) {
    GolemRegistry local;
    RevealService localReveal;
    NodeService localNodes;
    synchronized (GolemancyRuntime.class) {
      local = registry;
      registry = null;
      brains = null;
      localReveal = reveal;
      reveal = null;
      localNodes = nodes;
      nodes = null;
    }
    if (localNodes != null) {
      localNodes.shutdown();
    }
    if (localReveal != null) {
      localReveal.shutdown();
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

  public static Optional<GolemBrain> brain(Entity entity) {
    GolemBrains local = brains;
    return local == null ? Optional.empty() : local.brain(entity);
  }

  public static Optional<RevealService> reveal() {
    return Optional.ofNullable(reveal);
  }

  public static Optional<NodeService> nodes() {
    return Optional.ofNullable(nodes);
  }

  public static NodeSettings nodeSettings() {
    return nodeSettings;
  }

  public static void configureNodes(NodeSettings next) {
    nodeSettings = Objects.requireNonNull(next);
  }

  public static Optional<NodeWeavingPolicy> nodeWeaving() {
    return Optional.ofNullable(nodeWeaving);
  }

  public static void setNodeWeaving(NodeWeavingPolicy policy) {
    nodeWeaving = Objects.requireNonNull(policy);
  }

  public static GolemSettings settings() {
    return settings;
  }

  public static GolemBrainSettings brainSettings() {
    return brainSettings;
  }

  public static void configureBrain(GolemBrainSettings next) {
    brainSettings = Objects.requireNonNull(next);
  }

  public static GolemTargetPolicy targetPolicy(GolemCoreType core) {
    return TARGET_POLICIES.getOrDefault(core, COMPANION);
  }

  public static void setTargetPolicy(GolemCoreType core, GolemTargetPolicy policy) {
    TARGET_POLICIES.put(Objects.requireNonNull(core), Objects.requireNonNull(policy));
  }

  public static RevealSettings revealSettings() {
    return revealSettings;
  }

  public static Optional<Adaptation<?>> protectionPolicy() {
    return Optional.ofNullable(protectionPolicy);
  }

  public static void setProtectionPolicy(Adaptation<?> policy) {
    protectionPolicy = Objects.requireNonNull(policy);
  }

  public static GolemBindListener bindListener() {
    return bindListener;
  }

  public static void setBindListener(GolemBindListener listener) {
    bindListener = Objects.requireNonNull(listener);
  }

  public static void configure(GolemSettings next) {
    GolemSettings previous = settings;
    settings = Objects.requireNonNull(next);
    GolemRegistry local = registry;
    if (local != null && next.serverMaxActiveGolems() < previous.serverMaxActiveGolems()) {
      local.enforceCap();
    }
  }

  public static void configureReveal(RevealSettings next) {
    revealSettings = Objects.requireNonNull(next);
    RevealService local = reveal;
    if (local != null) {
      local.setInterval(next.pollMillis());
    }
  }

  static Optional<GolemController> controllerFor(GolemHandle handle) {
    if (!PaperCompat.supportsPathfinding()) {
      return Optional.empty();
    }
    Mob golem = handle.entity();
    return switch (handle.body()) {
      case COPPER -> golem instanceof CopperGolem copper
          ? Optional.of(new CopperGolemController(copper, () -> brainSettings.copperRepathTicks()))
          : Optional.empty();
      case FROST, IRON -> PaperCompat.hasClass(MOB_GOALS_CLASS)
          ? Optional.of(new GoalGolemController(golem, handle.body(), Bukkit.getMobGoals()))
          : Optional.empty();
      case BONE -> Optional.empty();
    };
  }

  private static void adoptLoaded(GolemRegistry target) {
    if (J.isFoliaThreading()) {
      return;
    }
    for (World world : Bukkit.getWorlds()) {
      for (Mob mob : world.getEntitiesByClass(Mob.class)) {
        if (GolemRegistry.adoptable(mob)) {
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
    public Player player(UUID owner) {
      return Bukkit.getPlayer(owner);
    }

    @Override
    public Optional<GolemRoster> roster(UUID owner) {
      AdaptPlayer player = onlinePlayer(owner);
      return player == null ? Optional.empty() : Optional.of(player.getData().getGolemancy());
    }

    @Override
    public boolean ownerReady(UUID owner) {
      AdaptPlayer player = onlinePlayer(owner);
      return player != null && player.isRuntimeReady();
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
    public boolean runEntity(Entity entity, Runnable task, Runnable retired) {
      return J.runEntity(entity, task, retired);
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
          if (entity instanceof Mob mob && GolemRegistry.adoptable(mob)) {
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
    public AttributeInstance attribute(LivingEntity entity, GolemStat stat) {
      Attribute attribute = stat.attribute();
      return attribute == null ? null : entity.getAttribute(attribute);
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
