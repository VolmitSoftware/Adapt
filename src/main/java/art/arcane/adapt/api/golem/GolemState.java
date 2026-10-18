package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.nms.NMS;
import art.arcane.adapt.util.common.io.Json;
import com.google.gson.JsonParseException;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemState {
  public static final NamespacedKey KEY = new NamespacedKey("adapt", "golem");
  public static final int MAX_CORES = 2;
  private static final Set<UUID> UNREADABLE_WARNED = ConcurrentHashMap.newKeySet();

  private UUID id;
  private UUID owner;
  private String body;
  private List<String> cores = new ArrayList<>();
  private List<Rune> runes = new ArrayList<>();
  private int channels;
  private String mode;
  private Anchor home;
  private Anchor homeChest;
  private List<String> carried = new ArrayList<>();
  private double essence;
  private double hostileDamagePool;
  private Stats stats = new Stats();
  private boolean masterwork;
  private boolean colossus;
  private boolean dormant;
  private boolean previouslyBound;
  private boolean snowTrail;
  private double maxHealthMark;
  private transient int carriedRevision;

  private GolemState() {
  }

  public static GolemState create(Seed seed) {
    GolemState state = new GolemState();
    state.id = Objects.requireNonNull(seed.id());
    state.owner = Objects.requireNonNull(seed.owner());
    state.body = seed.body().id();
    state.cores.add(seed.core().id());
    state.mode = GolemMode.defaultFor(seed.core()).id();
    state.home = seed.home();
    return state;
  }

  static GolemState restored(GolemSnapshot snapshot, Anchor fallbackHome) {
    GolemState state = new GolemState();
    state.id = snapshot.id();
    state.owner = snapshot.owner();
    state.body = snapshot.body().id();
    state.setCores(snapshot.cores());
    state.setRunes(snapshot.runes());
    state.setChannels(snapshot.channels());
    state.mode = snapshot.mode().id();
    state.home = snapshot.home() == null ? Objects.requireNonNull(fallbackHome) : snapshot.home();
    state.homeChest = snapshot.homeChest();
    state.carried = new ArrayList<>(snapshot.carried());
    state.setEssence(snapshot.essence());
    GolemSnapshot.Stats stats = snapshot.stats();
    state.stats.addKills(stats.kills());
    state.stats.addDamage(stats.damage());
    state.stats.addTasks(stats.tasks());
    state.stats.addItemsMoved(stats.itemsMoved());
    state.masterwork = snapshot.masterwork();
    state.snowTrail = snapshot.snowTrail();
    state.setMaxHealthMark(snapshot.maxHealthMark());
    state.previouslyBound = true;
    return state;
  }

  public static Optional<GolemState> fromJson(String json) {
    if (json == null || json.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(parse(json));
    } catch (JsonParseException | IllegalArgumentException | IllegalStateException | ClassCastException malformed) {
      return Optional.empty();
    }
  }

  public static boolean has(Entity entity) {
    return entity != null && entity.getPersistentDataContainer().has(KEY, PersistentDataType.STRING);
  }

  public static Optional<GolemState> read(Entity entity) {
    if (entity == null) {
      return Optional.empty();
    }
    String json = entity.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(parse(json));
    } catch (JsonParseException | IllegalArgumentException | IllegalStateException | ClassCastException malformed) {
      if (UNREADABLE_WARNED.add(entity.getUniqueId())) {
        Adapt.warn("Unreadable adapt:golem state on entity " + entity.getUniqueId() + ": " + malformed.getMessage(), malformed);
      }
      return Optional.empty();
    }
  }

  public static void clear(Entity entity) {
    entity.getPersistentDataContainer().remove(KEY);
  }

  public static String encodeItem(ItemStack item) {
    return NMS.serializeStack(item);
  }

  public String toJson() {
    return Json.toJson(this, false);
  }

  public void write(Entity entity) {
    PersistentDataContainer data = entity.getPersistentDataContainer();
    data.set(KEY, PersistentDataType.STRING, toJson());
  }

  public UUID id() {
    return id;
  }

  public UUID owner() {
    return owner;
  }

  public GolemBody body() {
    return GolemBody.fromId(body).orElseThrow();
  }

  public List<GolemCoreType> cores() {
    List<GolemCoreType> resolved = new ArrayList<>(cores.size());
    for (String core : cores) {
      GolemCoreType.fromId(core).ifPresent(resolved::add);
    }
    return Collections.unmodifiableList(resolved);
  }

  public GolemCoreType primaryCore() {
    List<GolemCoreType> resolved = cores();
    return resolved.isEmpty() ? GolemCoreType.BLANK : resolved.getFirst();
  }

  public List<GolemRune> runes() {
    List<GolemRune> resolved = new ArrayList<>(runes.size());
    for (Rune rune : runes) {
      GolemRune parsed = rune.resolve();
      if (parsed != null) {
        resolved.add(parsed);
      }
    }
    return Collections.unmodifiableList(resolved);
  }

  public int channels() {
    return channels;
  }

  public GolemMode mode() {
    return GolemMode.fromId(mode).orElse(GolemMode.defaultFor(primaryCore()));
  }

  public Anchor home() {
    return home;
  }

  public Anchor homeChest() {
    return homeChest;
  }

  public List<String> carried() {
    return Collections.unmodifiableList(carried);
  }

  public List<ItemStack> carriedItems() {
    List<ItemStack> items = new ArrayList<>(carried.size());
    for (String encoded : carried) {
      try {
        items.add(NMS.deserializeStack(encoded));
      } catch (RuntimeException corrupt) {
        Adapt.warn("Skipping unreadable carried item on golem " + id + "; the stored entry is kept.", corrupt);
      }
    }
    return items;
  }

  public double essence() {
    return essence;
  }

  public int essencePoints() {
    return (int) Math.max(0D, essence);
  }

  public double hostileDamagePool() {
    return hostileDamagePool;
  }

  public Stats stats() {
    return stats;
  }

  public boolean masterwork() {
    return masterwork;
  }

  public boolean colossus() {
    return colossus;
  }

  public boolean dormant() {
    return dormant;
  }

  public boolean previouslyBound() {
    return previouslyBound;
  }

  public boolean snowTrail() {
    return snowTrail;
  }

  public double maxHealthMark() {
    return maxHealthMark;
  }

  void setCores(List<GolemCoreType> next) {
    if (next.isEmpty() || next.size() > MAX_CORES) {
      throw new IllegalArgumentException("A golem holds 1 to " + MAX_CORES + " cores, found " + next.size());
    }
    List<String> ids = new ArrayList<>(next.size());
    for (GolemCoreType core : next) {
      ids.add(core.id());
    }
    cores = ids;
  }

  void setRunes(List<GolemRune> next) {
    List<Rune> stored = new ArrayList<>(next.size());
    for (GolemRune rune : next) {
      stored.add(new Rune(rune));
    }
    runes = stored;
  }

  void setChannels(int mask) {
    channels = ChannelMask.normalize(mask);
  }

  void setMode(GolemMode next) {
    mode = next.id();
  }

  void setHome(Anchor next) {
    home = next;
  }

  void setHomeChest(Anchor next) {
    homeChest = next;
  }

  void setCarried(List<String> encoded) {
    carried = new ArrayList<>(encoded);
    carriedRevision++;
  }

  int carriedRevision() {
    return carriedRevision;
  }

  void setEssence(double value) {
    essence = Math.max(0D, value);
  }

  void setHostileDamagePool(double value) {
    hostileDamagePool = Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  void setMasterwork(boolean value) {
    masterwork = value;
  }

  void setColossus(boolean value) {
    colossus = value;
  }

  void setDormant(boolean value) {
    dormant = value;
  }

  void setPreviouslyBound(boolean value) {
    previouslyBound = value;
  }

  void setSnowTrail(boolean value) {
    snowTrail = value;
  }

  void setMaxHealthMark(double value) {
    maxHealthMark = Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  private static GolemState parse(String json) {
    GolemState state = Json.fromJson(json, GolemState.class);
    if (state == null || state.id == null || state.owner == null || GolemBody.fromId(state.body).isEmpty()) {
      throw new IllegalStateException("the state is missing its id, owner or body");
    }
    return state.normalized();
  }

  private GolemState normalized() {
    if (cores == null) {
      cores = new ArrayList<>();
    }
    if (runes == null) {
      runes = new ArrayList<>();
    }
    if (carried == null) {
      carried = new ArrayList<>();
    }
    if (stats == null) {
      stats = new Stats();
    }
    cores.removeIf(core -> GolemCoreType.fromId(core).isEmpty());
    runes.removeIf(rune -> rune == null || rune.resolve() == null);
    channels = ChannelMask.normalize(channels);
    setHostileDamagePool(hostileDamagePool);
    return this;
  }

  public record Seed(UUID id, UUID owner, GolemBody body, GolemCoreType core, Anchor home) {
  }

  public static final class Anchor {
    private UUID world;
    private int x;
    private int y;
    private int z;

    private Anchor() {
    }

    public static Anchor of(UUID world, int x, int y, int z) {
      Anchor anchor = new Anchor();
      anchor.world = Objects.requireNonNull(world);
      anchor.x = x;
      anchor.y = y;
      anchor.z = z;
      return anchor;
    }

    public static Anchor at(Location location) {
      World world = Objects.requireNonNull(location.getWorld());
      return of(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public UUID world() {
      return world;
    }

    public int x() {
      return x;
    }

    public int y() {
      return y;
    }

    public int z() {
      return z;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Anchor anchor && x == anchor.x && y == anchor.y && z == anchor.z && Objects.equals(world, anchor.world);
    }

    @Override
    public int hashCode() {
      return Objects.hash(world, x, y, z);
    }

    @Override
    public String toString() {
      return world + ":" + x + "," + y + "," + z;
    }
  }

  public static final class Stats {
    private long kills;
    private double damage;
    private long tasks;
    private long itemsMoved;

    private Stats() {
    }

    public long kills() {
      return kills;
    }

    public double damage() {
      return damage;
    }

    public long tasks() {
      return tasks;
    }

    public long itemsMoved() {
      return itemsMoved;
    }

    void addKills(long amount) {
      kills += Math.max(0L, amount);
    }

    void addDamage(double amount) {
      if (Double.isFinite(amount) && amount > 0D) {
        damage += amount;
      }
    }

    void addTasks(long amount) {
      tasks += Math.max(0L, amount);
    }

    void addItemsMoved(long amount) {
      itemsMoved += Math.max(0L, amount);
    }
  }

  private static final class Rune {
    private String aspect;
    private int tier;

    private Rune() {
    }

    private Rune(GolemRune rune) {
      aspect = rune.aspect().id();
      tier = rune.tier();
    }

    private GolemRune resolve() {
      if (!GolemRuneType.isTier(tier)) {
        return null;
      }
      return GolemRuneType.fromId(aspect).map(type -> new GolemRune(type, tier)).orElse(null);
    }
  }
}
