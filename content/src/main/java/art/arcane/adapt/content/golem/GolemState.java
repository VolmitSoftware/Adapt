package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemState {
  public static final DataKey<String> KEY = DataKey.of(Id.of("adapt", "golem"), DataCodecs.STRING);
  public static final int MAX_CORES = 2;
  private static final Set<UUID> UNREADABLE_WARNED = ConcurrentHashMap.newKeySet();

  private UUID id;
  private UUID owner;
  private String body;
  private List<String> cores = new ArrayList<>();
  private List<Rune> runes = new ArrayList<>();
  private int channels;
  private String mode;
  private GolemAnchor home;
  private GolemAnchor homeChest;
  private List<String> carried = new ArrayList<>();
  private String held;
  private Map<String, String> equipment = new LinkedHashMap<>();
  private double essence;
  private double hostileDamagePool;
  private Stats stats = new Stats();
  private boolean masterwork;
  private boolean colossus;
  private boolean dormant;
  private boolean previouslyBound;
  private boolean snowTrail;
  private boolean combatOff;
  private double maxHealthMark;
  private long sighting;
  private Map<String, Integer> ownerLevels = new HashMap<>();
  private String customName;
  private transient int carriedRevision;
  private transient GolemBody bodyValue;
  private transient List<GolemCoreType> coresValue;
  private transient GolemMode modeValue;

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

  static GolemState restored(GolemSnapshot snapshot, GolemAnchor fallbackHome, long sighting) {
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
    state.held = snapshot.held();
    for (Map.Entry<GolemEquipmentSlot, String> entry : snapshot.equipment().entrySet()) {
      state.setEquipment(entry.getKey(), entry.getValue());
    }
    state.setEssence(snapshot.essence());
    GolemSnapshot.Stats stats = snapshot.stats();
    state.stats.addKills(stats.kills());
    state.stats.addDamage(stats.damage());
    state.stats.addTasks(stats.tasks());
    state.stats.addItemsMoved(stats.itemsMoved());
    state.masterwork = snapshot.masterwork();
    state.snowTrail = snapshot.snowTrail();
    state.combatOff = snapshot.combatOff();
    state.setMaxHealthMark(snapshot.maxHealthMark());
    state.setSighting(sighting);
    state.previouslyBound = true;
    return state;
  }

  public static boolean has(EntityRef entity) {
    return entity != null && entity.data().has(KEY);
  }

  public static Optional<GolemState> read(EntityRef entity) {
    if (entity == null) {
      return Optional.empty();
    }
    String json = entity.data().get(KEY);
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(parse(json));
    } catch (IllegalArgumentException | IllegalStateException | ClassCastException malformed) {
      if (UNREADABLE_WARNED.add(entity.uuid())) {
        AdaptApi.log().error("Unreadable adapt:golem state on entity " + entity.uuid() + ": " + malformed.getMessage(), malformed);
      }
      return Optional.empty();
    }
  }

  public static void clear(EntityRef entity) {
    entity.data().remove(KEY);
  }

  public static String encodeItem(ItemRef item) {
    return AdaptApi.platform().host().items().encode(item);
  }

  /** Decodes a stored item; throws {@link IllegalArgumentException} when the entry is unreadable. */
  public static ItemRef decodeItem(String encoded) {
    ItemRef item = AdaptApi.platform().host().items().decode(encoded);
    if (item == null || item.isEmpty()) {
      throw new IllegalArgumentException("Unreadable stored item");
    }
    return item;
  }

  public String toJson() {
    return Json.writeComponent(this);
  }

  public void write(EntityRef entity) {
    entity.data().set(KEY, toJson());
  }

  public UUID id() {
    return id;
  }

  public UUID owner() {
    return owner;
  }

  public GolemBody body() {
    GolemBody value = bodyValue;
    if (value == null) {
      value = GolemBody.fromId(body).orElseThrow();
      bodyValue = value;
    }
    return value;
  }

  public List<GolemCoreType> cores() {
    List<GolemCoreType> value = coresValue;
    if (value == null) {
      List<GolemCoreType> resolved = new ArrayList<>(cores.size());
      for (String core : cores) {
        GolemCoreType.fromId(core).ifPresent(resolved::add);
      }
      value = Collections.unmodifiableList(resolved);
      coresValue = value;
    }
    return value;
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
    GolemMode value = modeValue;
    if (value == null) {
      value = GolemMode.fromId(mode).orElse(GolemMode.defaultFor(primaryCore()));
      modeValue = value;
    }
    return value;
  }

  public GolemAnchor home() {
    return home;
  }

  public GolemAnchor homeChest() {
    return homeChest;
  }

  public List<String> carried() {
    return Collections.unmodifiableList(carried);
  }

  public List<ItemRef> carriedItems() {
    List<ItemRef> items = new ArrayList<>(carried.size());
    for (String encoded : carried) {
      try {
        items.add(decodeItem(encoded));
      } catch (RuntimeException corrupt) {
        AdaptApi.log().error("Skipping unreadable carried item on golem " + id + "; the stored entry is kept.", corrupt);
      }
    }
    return items;
  }

  public String held() {
    return held;
  }

  public Map<GolemEquipmentSlot, String> equipment() {
    Map<GolemEquipmentSlot, String> worn = new EnumMap<>(GolemEquipmentSlot.class);
    for (Map.Entry<String, String> entry : equipment.entrySet()) {
      GolemEquipmentSlot.fromId(entry.getKey()).ifPresent(slot -> worn.put(slot, entry.getValue()));
    }
    return Collections.unmodifiableMap(worn);
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

  public boolean combatOff() {
    return combatOff;
  }

  public double maxHealthMark() {
    return maxHealthMark;
  }

  public long sighting() {
    return sighting;
  }

  public Optional<String> name() {
    return GolemSnapshot.readableName(customName) ? Optional.of(customName) : Optional.empty();
  }

  public String customName() {
    return customName;
  }

  public int ownerLevel(String adaptation) {
    Integer level = ownerLevels == null ? null : ownerLevels.get(adaptation);
    return level == null ? 0 : level;
  }

  void setName(String name) {
    customName = name;
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
    coresValue = null;
    modeValue = null;
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
    modeValue = next;
  }

  void setHome(GolemAnchor next) {
    home = next;
  }

  void setHomeChest(GolemAnchor next) {
    homeChest = next;
  }

  void setCarried(List<String> encoded) {
    carried = new ArrayList<>(encoded);
    carriedRevision++;
  }

  void setHeld(String encoded) {
    held = encoded;
  }

  void setEquipment(GolemEquipmentSlot slot, String encoded) {
    if (encoded == null) {
      equipment.remove(slot.id());
      return;
    }
    equipment.put(slot.id(), encoded);
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

  boolean setOwnerLevel(String adaptation, int level) {
    if (ownerLevels == null) {
      ownerLevels = new HashMap<>();
    }
    Integer previous = ownerLevels.put(adaptation, level);
    return previous == null || previous != level;
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

  void setCombatOff(boolean value) {
    combatOff = value;
  }

  void setMaxHealthMark(double value) {
    maxHealthMark = Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  void setSighting(long value) {
    sighting = Math.max(0L, value);
  }

  private static GolemState parse(String json) {
    GolemState state = Json.read(json, GolemState.class);
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
    if (equipment == null) {
      equipment = new LinkedHashMap<>();
    }
    if (stats == null) {
      stats = new Stats();
    }
    equipment.entrySet().removeIf(entry -> GolemEquipmentSlot.fromId(entry.getKey()).isEmpty() || entry.getValue() == null || entry.getValue().isBlank());
    cores.removeIf(core -> GolemCoreType.fromId(core).isEmpty());
    runes.removeIf(rune -> rune == null || rune.resolve() == null);
    bodyValue = null;
    coresValue = null;
    modeValue = null;
    channels = ChannelMask.normalize(channels);
    setHostileDamagePool(hostileDamagePool);
    setSighting(sighting);
    return this;
  }

  public record Seed(UUID id, UUID owner, GolemBody body, GolemCoreType core, GolemAnchor home) {
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
