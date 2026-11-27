package art.arcane.adapt.platform;

import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

/**
 * Root platform facts and lookups. Lookups return null when nothing matches. {@link #onlinePlayers()}
 * and {@link #player(UUID)} are safe on any thread; the returned handles still follow their own
 * threading rules.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface PlatformHost {
  /** The server software family. */
  PlatformFamily family();

  /** Human-readable platform name, for example {@code Paper}, {@code Folia} or {@code NeoForge}. */
  String platformName();

  /** Minecraft version string, for example {@code 26.3}. */
  String minecraftVersion();

  /** {@link #minecraftVersion()} as a comparable version. */
  default GameVersion gameVersion() {
    return GameVersion.parse(minecraftVersion());
  }

  /** True when the running Minecraft version has the engine feature. */
  boolean supports(EngineFeature feature);

  /** Version of the running Adapt build, as written in the plugin or mod metadata. */
  String adaptVersion();

  /** True when the optional integration or feature is available; see {@link Capability}. */
  boolean has(Capability capability);

  /** Adapt's data directory: {@code plugins/Adapt} on Bukkit, {@code config/adapt} on modded. */
  Path dataDirectory();

  /** Adapt's logger. */
  PlatformLog log();

  /** Players online now, as a snapshot. */
  Collection<PlayerRef> onlinePlayers();

  /** Online player by id, or null when they are offline. */
  PlayerRef player(UUID id);

  /** Online player by exact name (case-insensitive), or null when no such player is online. */
  PlayerRef player(String name);

  /** Id of a player who joined before, by exact name (case-insensitive), from the server's profile cache, or null. */
  UUID knownPlayerId(String name);

  /** Last known name of a player who joined before, or null. */
  String knownPlayerName(UUID id);

  /** Loaded entity by id in any world, or null. Must be called on a thread that owns it. */
  EntityRef entity(UUID id);

  /** Every loaded world, as a snapshot. */
  Collection<WorldRef> worlds();

  /** Loaded world by its unique id, or null. */
  WorldRef world(UUID id);

  /** Loaded world by name, or null. */
  WorldRef world(String name);

  /** Loaded world by dimension key, for example {@code minecraft:the_nether}, or null. */
  WorldRef world(Id dimension);

  /** True when another plugin or mod with this id is present and enabled. */
  boolean isPresent(String pluginOrModId);

  /** Read-only game registries. */
  RegistryLookup registries();

  /** Item creation and item-type facts. */
  ItemOps items();

  /** Breeding, shearing and other animal state. */
  AnimalOps animals();

  /** Server tick counter. */
  long currentTick();

  /** Server view distance in chunks. */
  int viewDistance();

  /**
   * Runs a console command as the server. Callable from any thread: it runs inline on the server thread and is
   * queued for the next tick otherwise (on the global region on Folia). A null or blank command does nothing.
   */
  void dispatchConsoleCommand(String command);
}
