package art.arcane.adapt.platform;

import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

/**
 * Root platform facts and lookups. Lookups return null when nothing matches. {@link #onlinePlayers()}
 * and {@link #player(UUID)} are safe on any thread; the returned handles still follow their own
 * threading rules.
 */
public interface PlatformHost {
  PlatformFamily family();

  /** Human-readable platform name, for example {@code Paper}, {@code Folia} or {@code NeoForge}. */
  String platformName();

  /** Minecraft version string, for example {@code 26.3}. */
  String minecraftVersion();

  String adaptVersion();

  boolean has(Capability capability);

  /** Adapt's data directory: {@code plugins/Adapt} on Bukkit, {@code config/adapt} on modded. */
  Path dataDirectory();

  PlatformLog log();

  Collection<PlayerRef> onlinePlayers();

  PlayerRef player(UUID id);

  PlayerRef player(String name);

  /** Id of a player who joined before, by exact name (case-insensitive), from the server's profile cache, or null. */
  UUID knownPlayerId(String name);

  /** Last known name of a player who joined before, or null. */
  String knownPlayerName(UUID id);

  /** Loaded entity by id in any world, or null. Must be called on a thread that owns it. */
  EntityRef entity(UUID id);

  Collection<WorldRef> worlds();

  WorldRef world(UUID id);

  WorldRef world(String name);

  WorldRef world(Id dimension);

  /** True when another plugin or mod with this id is present and enabled. */
  boolean isPresent(String pluginOrModId);

  RegistryLookup registries();

  ItemOps items();

  AnimalOps animals();

  /** Server tick counter. */
  long currentTick();

  /** Server view distance in chunks. */
  int viewDistance();

  /** Runs a console command as the server. */
  void dispatchConsoleCommand(String command);
}
