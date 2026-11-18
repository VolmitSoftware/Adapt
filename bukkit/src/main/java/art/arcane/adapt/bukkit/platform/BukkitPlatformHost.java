package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.platform.entity.BukkitAnimalOps;
import art.arcane.adapt.bukkit.platform.item.BukkitItemOps;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.Capability;
import art.arcane.adapt.platform.PlatformFamily;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Root platform host over the Bukkit server and the Adapt plugin. */
public final class BukkitPlatformHost implements PlatformHost {
  static final List<String> PROTECTION_PLUGINS = List.of(
      "WorldGuard", "Factions", "ChestProtect", "Residence", "GriefDefender", "GriefPrevention", "LockettePro");

  private final Server server;
  private final Plugin plugin;
  private final PlatformLog log;
  private final AdaptScheduler scheduler;
  private final RegistryLookup registries = new BukkitRegistryLookup();
  private final ItemOps items = new BukkitItemOps();
  private final AnimalOps animals = new BukkitAnimalOps();

  public BukkitPlatformHost(Server server, Plugin plugin, PlatformLog log, AdaptScheduler scheduler) {
    this.server = Objects.requireNonNull(server, "server");
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.log = Objects.requireNonNull(log, "log");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
  }

  @Override
  public PlatformFamily family() {
    return PlatformFamily.BUKKIT;
  }

  @Override
  public String platformName() {
    return server.getName();
  }

  @Override
  public String minecraftVersion() {
    return server.getMinecraftVersion();
  }

  @Override
  @SuppressWarnings("deprecation")
  public String adaptVersion() {
    return plugin.getDescription().getVersion();
  }

  @Override
  public boolean has(Capability capability) {
    return switch (capability) {
      case FOLIA_REGIONS -> scheduler.isRegionized();
      case PLACEHOLDERS -> isPresent("PlaceholderAPI");
      case ECONOMY -> isPresent("Vault");
      case PROTECTION_PLUGINS -> anyPresent(PROTECTION_PLUGINS);
      case WORLDGUARD -> isPresent("WorldGuard");
      case HIDDEN_ORE -> isPresent("HiddenOre");
      case ADVANCED_CHESTS -> isPresent("AdvancedChests");
      case MAGIC_COSMETICS -> isPresent("MagicCosmetics");
    };
  }

  @Override
  public Path dataDirectory() {
    return plugin.getDataFolder().toPath();
  }

  @Override
  public PlatformLog log() {
    return log;
  }

  @Override
  public Collection<PlayerRef> onlinePlayers() {
    return BukkitRefs.players(server.getOnlinePlayers());
  }

  @Override
  public PlayerRef player(UUID id) {
    if (id == null) {
      return null;
    }
    Player player = server.getPlayer(id);
    return player == null ? null : BukkitRefs.player(player);
  }

  @Override
  public PlayerRef player(String name) {
    if (name == null) {
      return null;
    }
    Player player = server.getPlayerExact(name);
    return player == null ? null : BukkitRefs.player(player);
  }

  @Override
  public UUID knownPlayerId(String name) {
    if (name == null) {
      return null;
    }
    OfflinePlayer cached = server.getOfflinePlayerIfCached(name);
    return cached == null ? null : cached.getUniqueId();
  }

  @Override
  public String knownPlayerName(UUID id) {
    return id == null ? null : server.getOfflinePlayer(id).getName();
  }

  @Override
  public EntityRef entity(UUID id) {
    if (id == null) {
      return null;
    }
    Entity entity = server.getEntity(id);
    return entity == null ? null : BukkitRefs.entity(entity);
  }

  @Override
  public Collection<WorldRef> worlds() {
    List<World> worlds = server.getWorlds();
    List<WorldRef> wrapped = new ArrayList<>(worlds.size());
    for (World world : worlds) {
      wrapped.add(BukkitRefs.world(world));
    }
    return wrapped;
  }

  @Override
  public WorldRef world(UUID id) {
    return id == null ? null : BukkitRefs.world(server.getWorld(id));
  }

  @Override
  public WorldRef world(String name) {
    return name == null ? null : BukkitRefs.world(server.getWorld(name));
  }

  @Override
  public WorldRef world(Id dimension) {
    if (dimension == null) {
      return null;
    }
    NamespacedKey key = BukkitConversions.key(dimension);
    return BukkitRefs.world(server.getWorld(key));
  }

  @Override
  public boolean isPresent(String pluginOrModId) {
    if (pluginOrModId == null || pluginOrModId.isEmpty()) {
      return false;
    }
    PluginManager manager = server.getPluginManager();
    return manager != null && manager.isPluginEnabled(pluginOrModId);
  }

  @Override
  public RegistryLookup registries() {
    return registries;
  }

  @Override
  public ItemOps items() {
    return items;
  }

  @Override
  public AnimalOps animals() {
    return animals;
  }

  @Override
  public long currentTick() {
    return server.getCurrentTick();
  }

  @Override
  public int viewDistance() {
    return server.getViewDistance();
  }

  @Override
  public void dispatchConsoleCommand(String command) {
    if (command == null || command.isBlank()) {
      return;
    }
    Runnable dispatch = () -> server.dispatchCommand(server.getConsoleSender(), command);
    if (!scheduler.isRegionized() && scheduler.isTickThread()) {
      dispatch.run();
    } else {
      scheduler.runGlobal(dispatch);
    }
  }

  private boolean anyPresent(List<String> names) {
    for (String name : names) {
      if (isPresent(name)) {
        return true;
      }
    }
    return false;
  }
}
