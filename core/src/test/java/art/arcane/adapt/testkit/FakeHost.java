package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.Capability;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.PlatformFamily;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform host over fake worlds and players. Every registry id exists and every engine feature is supported until
 * {@link #runGame} pins a Minecraft version; no capabilities.
 */
public final class FakeHost implements PlatformHost {
  private final List<FakeWorld> worlds = new ArrayList<>();
  private final List<PlayerRef> players = new ArrayList<>();
  private final List<String> commands = new ArrayList<>();
  private final FakeItems items;
  private final FakeAnimals animals = new FakeAnimals();
  private final Path dataDirectory;
  private final PlatformLog log;
  private final ManualScheduler scheduler;
  private final Map<Id, Set<Id>> blockTags = new ConcurrentHashMap<>();
  private final Map<UUID, EntityRef> trackedEntities = new ConcurrentHashMap<>();
  private final Map<Id, EffectCategory> effectCategories = new ConcurrentHashMap<>();
  private final Set<Id> instantEffects = ConcurrentHashMap.newKeySet();
  private final Map<Id, List<EffectInstance>> potionEffects = new ConcurrentHashMap<>();
  private final Map<Id, List<EffectModifier>> effectModifiers = new ConcurrentHashMap<>();
  private final Map<UUID, String> knownPlayers = new ConcurrentHashMap<>();

  public FakeHost(FakeItems items, Path dataDirectory, PlatformLog log, ManualScheduler scheduler) {
    this.items = items;
    this.dataDirectory = dataDirectory;
    this.log = log;
    this.scheduler = scheduler;
  }

  public FakeWorld world(String name, boolean create) {
    for (FakeWorld world : worlds) {
      if (world.name().equals(name)) {
        return world;
      }
    }
    if (!create) {
      return null;
    }
    FakeWorld world = new FakeWorld(name);
    worlds.add(world);
    return world;
  }

  /** Adds an online player to the host and its world. */
  public FakePlayer join(FakePlayer player) {
    players.add(player);
    ((FakeWorld) player.world()).add(player);
    return player;
  }

  public void quit(FakePlayer player) {
    player.setOnline(false);
    players.remove(player);
    player.remove();
  }

  public List<String> commands() {
    return List.copyOf(commands);
  }

  @Override
  public PlatformFamily family() {
    return PlatformFamily.FABRIC;
  }

  @Override
  public String platformName() {
    return "test";
  }

  /** Answers registries, version and features like a server of that version; null restores "everything exists". */
  public void runGame(FakeGameContent game) {
    this.game = game;
    items.knownItems(game == null ? (Id type) -> true : (Id type) -> game.exists(RegistryKind.ITEM, type));
  }

  @Override
  public String minecraftVersion() {
    FakeGameContent current = game;
    return current == null ? "26.3" : current.version().toString();
  }

  @Override
  public boolean supports(EngineFeature feature) {
    FakeGameContent current = game;
    return current == null || current.supports(feature);
  }

  @Override
  public String adaptVersion() {
    return "test";
  }

  @Override
  public boolean has(Capability capability) {
    return false;
  }

  @Override
  public Path dataDirectory() {
    return dataDirectory;
  }

  @Override
  public PlatformLog log() {
    return log;
  }

  @Override
  public Collection<PlayerRef> onlinePlayers() {
    return List.copyOf(players);
  }

  @Override
  public PlayerRef player(UUID id) {
    for (PlayerRef player : players) {
      if (player.uuid().equals(id)) {
        return player;
      }
    }
    return null;
  }

  @Override
  public PlayerRef player(String name) {
    for (PlayerRef player : players) {
      if (player.name().equalsIgnoreCase(name)) {
        return player;
      }
    }
    return null;
  }

  /** Records a player the server saw before, for offline name and id lookups. */
  public void know(UUID id, String name) {
    knownPlayers.put(id, name);
  }

  @Override
  public UUID knownPlayerId(String name) {
    for (Map.Entry<UUID, String> known : knownPlayers.entrySet()) {
      if (known.getValue().equalsIgnoreCase(name)) {
        return known.getKey();
      }
    }
    return null;
  }

  @Override
  public String knownPlayerName(UUID id) {
    return knownPlayers.get(id);
  }

  /** Makes a non-player entity resolvable through {@link #entity(UUID)}. */
  public void track(EntityRef entity) {
    trackedEntities.put(entity.uuid(), entity);
  }

  @Override
  public EntityRef entity(UUID id) {
    PlayerRef player = player(id);
    return player != null ? player : trackedEntities.get(id);
  }

  @Override
  public Collection<WorldRef> worlds() {
    return List.copyOf(worlds);
  }

  @Override
  public WorldRef world(UUID id) {
    for (FakeWorld world : worlds) {
      if (world.uid().equals(id)) {
        return world;
      }
    }
    return null;
  }

  @Override
  public WorldRef world(String name) {
    return world(name, false);
  }

  @Override
  public WorldRef world(Id dimension) {
    for (FakeWorld world : worlds) {
      if (world.key().equals(dimension)) {
        return world;
      }
    }
    return null;
  }

  @Override
  public boolean isPresent(String pluginOrModId) {
    return false;
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
  public FakeAnimals animals() {
    return animals;
  }

  @Override
  public long currentTick() {
    return scheduler.tick();
  }

  @Override
  public int viewDistance() {
    return 10;
  }

  @Override
  public void dispatchConsoleCommand(String command) {
    commands.add(command);
  }

  /** Registers a mob effect's category and whether it is instant; unregistered effects are unknown. */
  /** Sets the effects of a potion type for {@link RegistryLookup#potionEffects}. */
  public void potion(Id potion, EffectInstance... effects) {
    potionEffects.put(potion, List.of(effects));
  }

  /** Sets the attribute modifiers an effect applies; amounts scale with amplifier + 1 like vanilla. */
  public void effectModifiers(Id effect, EffectModifier... modifiers) {
    effectModifiers.put(effect, List.of(modifiers));
  }

  public void effect(Id effect, EffectCategory category, boolean instant) {
    effectCategories.put(effect, category);
    if (instant) {
      instantEffects.add(effect);
    } else {
      instantEffects.remove(effect);
    }
  }

  /** Sets the members of a block tag; other tags and registries stay empty. */
  public void tagBlocks(Id tag, Id... blocks) {
    blockTags.put(tag, Set.of(blocks));
  }

  /** Makes one registry id unknown; every other id keeps existing. */
  public void removeRegistryId(RegistryKind kind, Id id) {
    missingIds.add(kind.name() + "|" + id);
  }

  private final Set<String> missingIds = ConcurrentHashMap.newKeySet();
  private volatile FakeGameContent game;

  private final RegistryLookup registries = new RegistryLookup() {
    @Override
    public boolean exists(RegistryKind kind, Id id) {
      FakeGameContent current = game;
      return !missingIds.contains(kind.name() + "|" + id) && (current == null || current.exists(kind, id));
    }

    @Override
    public Set<Id> ids(RegistryKind kind) {
      FakeGameContent current = game;
      return current == null ? Set.of() : current.ids(kind);
    }

    @Override
    public boolean isTagged(RegistryKind kind, Id tag, Id value) {
      return kind == RegistryKind.BLOCK && blockTags.getOrDefault(tag, Set.of()).contains(value);
    }

    @Override
    public Set<Id> tagValues(RegistryKind kind, Id tag) {
      return kind == RegistryKind.BLOCK ? blockTags.getOrDefault(tag, Set.of()) : Set.of();
    }

    @Override
    public int enchantmentMaxLevel(Id enchantment) {
      return 5;
    }

    @Override
    public String particleDataType(Id particle) {
      return null;
    }

    @Override
    public EffectCategory effectCategory(Id effect) {
      return effectCategories.get(effect);
    }

    @Override
    public boolean isInstantEffect(Id effect) {
      return instantEffects.contains(effect);
    }

    @Override
    public List<EffectInstance> potionEffects(Id potion) {
      return potionEffects.getOrDefault(potion, List.of());
    }

    @Override
    public List<EffectModifier> effectModifiers(Id effect, int amplifier) {
      List<EffectModifier> scaled = new ArrayList<>();
      for (EffectModifier modifier : effectModifiers.getOrDefault(effect, List.of())) {
        scaled.add(new EffectModifier(modifier.translationKey(), modifier.operation(), modifier.amount() * (amplifier + 1)));
      }
      return scaled;
    }
  };
}
