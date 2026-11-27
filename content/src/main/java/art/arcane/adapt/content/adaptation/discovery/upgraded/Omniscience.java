package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryKeenEye;
import art.arcane.adapt.content.adaptation.discovery.DiscoverySixthSense;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryStructures;
import art.arcane.adapt.content.adaptation.discovery.FirstVisits;
import art.arcane.adapt.content.adaptation.discovery.InsightLens;
import art.arcane.adapt.content.adaptation.discovery.InsightTier;
import art.arcane.adapt.content.adaptation.discovery.KeenEyeScan;
import art.arcane.adapt.content.adaptation.discovery.StructureSense;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerLootGenerateEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.platform.world.WorldRef;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Insight, Keen Eye and Sixth Sense fused: the creature you look at, the chests and spawners you can see and the
 * nearest structure share one readout, and the containers you can see glow for you. Its Explorer's Atlas keeps the
 * biomes and structures you found, your pins and waypoints, and at the search level finds unfound structures.
 */
public class Omniscience extends SkillUpgrade<Omniscience.Config> {
  public static final Id ID = Id.adapt("discovery/upgrade/omniscience");

  static final String ATLAS_STORAGE = "atlas";
  static final List<Id> SEARCHABLE = DiscoveryStructures.SENSED;
  private static final String HUD_GROUP = "upgraded-discovery-omniscience";
  private static final String SEPARATOR = TextFormat.DARK_GRAY + " | ";
  private static final String WAYPOINT_SYMBOL = "⚑";
  private static final String BIOME_SYMBOL = "❀";
  private static final String LEAD_SYMBOL = "⌖";
  private static final int MAX_NAME_LENGTH = 24;
  private static final long PROMPT_MILLIS = 30000L;
  private static final int PAPER_PER_SEARCH = 1;
  private static final Id PAPER = Id.minecraft("paper");
  private static final List<StructureAction> STRUCTURE_ACTIONS = new CopyOnWriteArrayList<>();
  private final ExplorersAtlas atlas;
  private final StructureSense sense = new StructureSense();
  private final StructureSense searches = new StructureSense();
  private final Map<UUID, DiscoverySixthSense.StructureTarget> structures = playerState();
  private final Map<UUID, UUID> focus = playerState();
  private final Map<UUID, AtlasRecord> records = playerState();
  private final Map<UUID, Search> searching = playerState();
  private final Map<UUID, Prompt> prompts = playerState();
  private final Map<UUID, String> covers = playerState();
  private final Set<UUID> showing = ConcurrentHashMap.newKeySet();
  private final Cooldowns readoutThrottle = cooldowns();
  private final Cooldowns pulseThrottle = cooldowns();
  private final Cooldowns recordThrottle = cooldowns();
  private final Cooldowns searchCooldowns = cooldowns();

  public Omniscience() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
    setInterval(50);
    atlas = registerTool(new ExplorersAtlas(this));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getReadoutRange(level), 0), 1);
    statLore(v, Format.f(getStructureRange(level), 0), 2);
    statLore(v, pinsAllowed(level), 3);
    statLore(v, waypointsAllowed(level), 4);
    if (searchUnlocked(level)) {
      statLore(v, Format.f(getConfig().searchRange, 0), 5);
    }
  }

  public ExplorersAtlas atlas() {
    return atlas;
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public boolean hasTickDemand() {
    return super.hasTickDemand() || sense.hasSweeps() || sense.hasWork() || searches.hasSweeps() || searches.hasWork()
        || !searching.isEmpty() || !showing.isEmpty();
  }

  @Override
  public void onTick() {
    sense.tick();
    searches.tick();
    for (UUID searcher : List.copyOf(searching.keySet())) {
      PlayerRef player = AdaptApi.platform().host().player(searcher);
      if (player == null || !player.isOnline()) {
        forget(searcher);
      } else {
        AdaptApi.scheduler().runEntity(player, () -> finishSearchOwned(player));
      }
    }
    long now = System.currentTimeMillis();
    for (PlayerProfile adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.player();
      if (player == null || !player.isOnline() || !readoutThrottle.isReady(player.uuid(), getConfig().readoutIntervalMillis)) {
        continue;
      }
      readoutThrottle.mark(player.uuid());
      AdaptApi.scheduler().runEntity(player, () -> readOwned(player));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    forget(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerLootGenerateEvent e) {
    PlayerRef player = e.player();
    if (getActiveLevel(player) <= 0) {
      return;
    }
    Loc at = e.location();
    AtlasRecord record = record(player);
    if (record == null || at.world() == null) {
      return;
    }
    AtlasRecord.Place place = record.structureAt(at.world().uid(), at.x(), at.y(), at.z());
    if (place == null) {
      for (GeneratedStructure structure : FirstVisits.structuresAround(at)) {
        place = record.recordStructure(structure, DiscoverySixthSense.structureName(structure.structure(), structure.type()),
            at, getConfig().maxStructures);
        if (place != null) {
          break;
        }
      }
    }
    if (place != null && !place.looted) {
      place.looted = true;
      save(player, record);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerChatEvent e) {
    PlayerRef player = e.player();
    Prompt prompt = prompts.get(player.uuid());
    if (prompt == null) {
      return;
    }
    if (AdaptApi.clock().millis() > prompt.expiresAt()) {
      prompts.remove(player.uuid(), prompt);
      return;
    }
    e.setCancelled(true);
    String message = e.message();
    AdaptApi.scheduler().runEntity(player, () -> answer(player, prompt, message));
  }

  @Override
  public void unregister() {
    sense.close();
    searches.close();
    structures.clear();
    focus.clear();
    records.clear();
    searching.clear();
    prompts.clear();
    covers.clear();
    showing.clear();
    super.unregister();
  }

  private void readOwned(PlayerRef player) {
    UUID id = player.uuid();
    int level = getActiveLevel(player);
    if (level <= 0) {
      if (showing.remove(id)) {
        HudStatus.clearAmbient(player, HUD_GROUP);
      }
      forget(id);
      return;
    }

    if (recordThrottle.isReady(id, getConfig().recordIntervalMillis)) {
      recordThrottle.mark(id);
      recordOwned(player);
    }

    String text = readout(player, level);
    covers.put(id, text);
    if (text.isEmpty()) {
      if (showing.remove(id)) {
        HudStatus.clearAmbient(player, HUD_GROUP);
      }
    } else {
      showing.add(id);
      HudStatus.ambient(player, HUD_GROUP, text);
    }

    Loc origin = player.location();
    if (!sense.running(id, origin.world().uid()) && pulseThrottle.isReady(id, getConfig().structurePulseMillis)) {
      pulseThrottle.mark(id);
      sense.begin(player, origin, getStructureRange(level), DiscoveryStructures.SENSED,
          (StructureSense.Sensed found) -> acceptStructure(player, found));
    }
  }

  String readout(PlayerRef player, int level) {
    List<String> parts = new ArrayList<>(4);
    double range = getReadoutRange(level);
    LivingRef creature = InsightLens.lookTarget(player, player.eyeLocation(), range);
    if (creature != null && !(creature instanceof PlayerRef other && !AdaptApi.platform().viewers().canSee(player, other))) {
      parts.add(InsightLens.actionBar(player, creature, InsightTier.of(getLevelPercent(level), true)));
      UUID previous = focus.put(player.uuid(), creature.uuid());
      if (!creature.uuid().equals(previous)) {
        cue(player, MutationCue.WORK);
      }
    }

    int chests = 0;
    int spawners = 0;
    for (BlockRef block : KeenEyeScan.scan(player, range, getConfig().viewConeCos, KeenEyeScan.HARD_MAX_HIGHLIGHTS, (Id type) -> true)) {
      boolean spawner = KeenEyeScan.isSpawner(block.type());
      if (spawner) {
        spawners++;
      } else {
        chests++;
      }
      KeenEyeScan.glimmer(this, player, block, spawner ? DiscoveryKeenEye.SPAWNER_COLOR : DiscoveryKeenEye.CHEST_COLOR,
          getConfig().outlineTicks);
    }
    if (chests > 0) {
      parts.add(TextFormat.GOLD + Text.key("adapt.upgrade.discovery.omniscience.chests").withMarkup("count", TextFormat.WHITE + String.valueOf(chests)).resolve());
    }
    if (spawners > 0) {
      parts.add(TextFormat.AQUA + Text.key("adapt.upgrade.discovery.omniscience.spawners").withMarkup("count", TextFormat.WHITE + String.valueOf(spawners)).resolve());
    }

    String structure = structureText(player, level);
    if (!structure.isEmpty()) {
      parts.add(structure);
    }
    parts.addAll(pinTexts(player, level));
    return String.join(SEPARATOR, parts);
  }

  /** The pinned entries and the last search find in this world, each with a private direction line. */
  private List<String> pinTexts(PlayerRef player, int level) {
    AtlasRecord record = record(player);
    if (record == null) {
      return List.of();
    }
    List<AtlasRecord.Place> shown = new ArrayList<>(record.pinned(pinsAllowed(level)));
    if (record.lead != null && !shown.contains(record.lead)) {
      shown.add(record.lead);
    }
    Loc origin = player.location();
    UUID worldId = origin.world().uid();
    List<String> texts = new ArrayList<>(shown.size());
    int lineTicks = (int) Math.max(6L, getConfig().readoutIntervalMillis / 50L + 6L);
    for (int index = 0; index < shown.size(); index++) {
      AtlasRecord.Place place = shown.get(index);
      if (!place.inWorld(worldId)) {
        continue;
      }
      double dx = place.x - origin.x();
      double dz = place.z - origin.z();
      double distance = Math.sqrt(dx * dx + dz * dz);
      if (distance >= 2D) {
        DiscoverySixthSense.pointTowards(this, player, "atlas-pin-" + index, dx / distance, dz / distance, distance, lineTicks);
      }
      texts.add(TextFormat.LIGHT_PURPLE + symbol(place) + " " + TextFormat.WHITE + displayName(place) + " " + TextFormat.YELLOW
          + DiscoverySixthSense.compassDirection(dx, dz) + " " + TextFormat.GRAY + Math.round(distance));
    }
    return texts;
  }

  /** A structure in a player's Atlas: its Atlas id, name, world and the spot it was found from. */
  public record Structure(String id, String name, UUID worldId, double x, double y, double z) {
  }

  /** Something other skills offer on a structure in the Atlas, shown on its right click. */
  public interface StructureAction {
    /** The click's label for this player and structure, or null when it is not offered. */
    @Nullable Text label(PlayerRef player, Structure structure);

    void run(PlayerRef player, Structure structure);
  }

  public static void addStructureAction(StructureAction action) {
    STRUCTURE_ACTIONS.add(action);
  }

  public static void removeStructureAction(StructureAction action) {
    STRUCTURE_ACTIONS.remove(action);
  }

  static List<StructureAction> structureActions() {
    return STRUCTURE_ACTIONS;
  }

  /** The structures in the player's Atlas; empty without one. */
  public List<Structure> structures(PlayerRef player) {
    AtlasRecord record = record(player);
    if (record == null) {
      return List.of();
    }
    List<Structure> found = new ArrayList<>(record.structures.size());
    for (AtlasRecord.Place place : record.structures) {
      Structure structure = structure(place);
      if (structure != null) {
        found.add(structure);
      }
    }
    return found;
  }

  static @Nullable Structure structure(AtlasRecord.Place place) {
    try {
      return new Structure(place.id, displayName(place), UUID.fromString(place.world), place.x, place.y, place.z);
    } catch (IllegalArgumentException | NullPointerException invalid) {
      return null;
    }
  }

  static String displayName(AtlasRecord.Place place) {
    return place.kind == AtlasRecord.Kind.BIOME || place.name == null
        ? DiscoverySixthSense.structureName(null, Id.parse(place.type))
        : place.name;
  }

  static String symbol(AtlasRecord.Place place) {
    return switch (place.kind) {
      case WAYPOINT -> WAYPOINT_SYMBOL;
      case BIOME -> BIOME_SYMBOL;
      case LEAD -> LEAD_SYMBOL;
      case STRUCTURE -> DiscoverySixthSense.structureSymbol(Id.parse(place.type));
    };
  }

  private String structureText(PlayerRef player, int level) {
    DiscoverySixthSense.StructureTarget target = structures.get(player.uuid());
    if (target == null) {
      return "";
    }
    Loc origin = player.location();
    double distance = Math.sqrt(target.distanceSquared(origin));
    if (!Double.isFinite(distance) || distance > getStructureRange(level)) {
      structures.remove(player.uuid());
      return "";
    }
    double dx = target.x() - origin.x();
    double dz = target.z() - origin.z();
    return Text.key("adapt.discovery.sixth_sense.hud").withMarkup("symbol", TextFormat.AQUA + target.symbol()).withMarkup("structure", TextFormat.WHITE + target.name()).withMarkup("direction", TextFormat.YELLOW + DiscoverySixthSense.compassDirection(dx, dz)).withMarkup("distance", TextFormat.GRAY + String.valueOf(Math.round(distance))).resolve();
  }

  private void acceptStructure(PlayerRef player, StructureSense.Sensed found) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    Loc origin = player.location();
    if (!found.worldId().equals(origin.world().uid())) {
      return;
    }
    DiscoverySixthSense.StructureTarget target = new DiscoverySixthSense.StructureTarget(found.worldId(), found.x(), found.z(),
        found.name(), found.symbol(), AdaptApi.clock().millis());
    double range = getStructureRange(level);
    if (target.distanceSquared(origin) > range * range) {
      return;
    }
    if (DiscoverySixthSense.shouldReplaceTarget(structures.get(player.uuid()), target, origin)) {
      structures.put(player.uuid(), target);
      cue(player, MutationCue.WORK);
    }
  }

  /** Adds the biome and the structures the player stands in to their Atlas. */
  void recordOwned(PlayerRef player) {
    AtlasRecord record = record(player);
    Loc at = player.location();
    WorldRef world = at.world();
    if (record == null || world == null) {
      return;
    }
    boolean changed = record.recordBiome(world.blockAt(at).biome(), at);
    for (GeneratedStructure structure : FirstVisits.structuresAround(at)) {
      AtlasRecord.Place place = record.recordStructure(structure,
          DiscoverySixthSense.structureName(structure.structure(), structure.type()), at, getConfig().maxStructures);
      if (place != null) {
        changed = true;
        cue(player, MutationCue.WORK);
      }
    }
    if (changed) {
      save(player, record);
    }
  }

  void openAtlas(PlayerRef player, int level) {
    AtlasRecord record = record(player);
    if (record != null) {
      AtlasGui.open(this, player, level, record, 0);
    }
  }

  /** The player's Atlas, read once from their Omniscience and kept while they are online; null without one. */
  AtlasRecord record(PlayerRef player) {
    AtlasRecord cached = records.get(player.uuid());
    if (cached != null) {
      return cached;
    }
    if (getLevel(player) <= 0) {
      return null;
    }
    AtlasRecord loaded = AtlasRecord.parse(getStorageString(player, ATLAS_STORAGE, null));
    records.put(player.uuid(), loaded);
    return loaded;
  }

  void save(PlayerRef player, AtlasRecord record) {
    setStorage(player, ATLAS_STORAGE, record.json());
  }

  AtlasRecord.Change togglePin(PlayerRef player, String id) {
    AtlasRecord record = record(player);
    if (record == null) {
      return AtlasRecord.Change.MISSING;
    }
    AtlasRecord.Change change = record.togglePin(id, pinsAllowed(getLevel(player)));
    if (change == AtlasRecord.Change.DONE) {
      save(player, record);
    } else if (change == AtlasRecord.Change.FULL) {
      player.sendMessage(TextFormat.RED + Text.key("adapt.tool.discovery.explorers_atlas.pins_full").withMarkup("max", String.valueOf(pinsAllowed(getLevel(player)))).resolve());
    }
    return change;
  }

  boolean removeWaypoint(PlayerRef player, String id) {
    AtlasRecord record = record(player);
    if (record == null || !record.removeWaypoint(id)) {
      return false;
    }
    save(player, record);
    return true;
  }

  /** Asks for a waypoint name in chat; the waypoint is set where the player stands when they answer. */
  void promptWaypoint(PlayerRef player) {
    prompts.put(player.uuid(), new Prompt(AdaptApi.clock().millis() + PROMPT_MILLIS));
    player.closeInventory();
    player.sendMessage(TextFormat.AQUA + Text.key("adapt.tool.discovery.explorers_atlas.name_prompt").resolve());
    player.sendMessage(Text.key("adapt.config.input.type_cancel").resolve());
  }

  private void answer(PlayerRef player, Prompt prompt, String message) {
    if (!prompts.remove(player.uuid(), prompt) || AdaptApi.clock().millis() > prompt.expiresAt() || message.trim().equalsIgnoreCase("cancel")) {
      return;
    }
    addWaypoint(player, message);
  }

  AtlasRecord.Change addWaypoint(PlayerRef player, String rawName) {
    AtlasRecord record = record(player);
    int level = getActiveLevel(player);
    String name = waypointName(rawName);
    if (record == null || level <= 0 || name.isEmpty()) {
      return AtlasRecord.Change.MISSING;
    }
    AtlasRecord.Change change = record.addWaypoint(name, player.location(), waypointsAllowed(level));
    if (change == AtlasRecord.Change.DONE) {
      save(player, record);
      cue(player, MutationCue.WORK);
      player.sendMessage(TextFormat.AQUA + Text.key("adapt.tool.discovery.explorers_atlas.waypoint_added").with("name", name).resolve());
    } else {
      player.sendMessage(TextFormat.RED + Text.key("adapt.tool.discovery.explorers_atlas.waypoints_full").withMarkup("max", String.valueOf(waypointsAllowed(level))).resolve());
    }
    return change;
  }

  static String waypointName(String raw) {
    String plain = TextFormat.strip(raw == null ? "" : raw).replace("&", "").replace("§", "").trim();
    return plain.length() > MAX_NAME_LENGTH ? plain.substring(0, MAX_NAME_LENGTH).trim() : plain;
  }

  void cycleSearchType(PlayerRef player) {
    AtlasRecord record = record(player);
    if (record != null) {
      record.searchType = Math.floorMod(record.searchType + 1, SEARCHABLE.size());
      save(player, record);
    }
  }

  long searchCooldownLeft(PlayerRef player) {
    return searchCooldowns.remaining(player.uuid(), getConfig().searchCooldownMillis);
  }

  boolean searchRunning(PlayerRef player) {
    return searching.containsKey(player.uuid());
  }

  /**
   * Starts a search for the nearest structure of the Atlas's chosen type that is not in it yet, over chunks that were
   * already generated, so nothing is loaded or generated. Costs paper and hunger up front and starts the cooldown.
   */
  boolean search(PlayerRef player) {
    int level = getActiveLevel(player);
    AtlasRecord record = record(player);
    UUID id = player.uuid();
    if (!searchUnlocked(level) || record == null || searching.containsKey(id)) {
      return false;
    }
    if (!searchCooldowns.isReady(id, getConfig().searchCooldownMillis)) {
      player.sendMessage(TextFormat.RED + Text.key("adapt.tool.discovery.explorers_atlas.search_cooldown").withMarkup("time", Format.duration(searchCooldownLeft(player), 0)).resolve());
      return false;
    }
    int hunger = Math.max(0, getConfig().searchHunger);
    int slot = paperSlot(player);
    if (slot < 0 || player.foodLevel() < hunger) {
      player.sendMessage(TextFormat.RED + Text.key("adapt.tool.discovery.explorers_atlas.search_cost").withMarkup("paper", String.valueOf(PAPER_PER_SEARCH)).withMarkup("hunger", String.valueOf(hunger)).resolve());
      return false;
    }
    ItemRef unit = player.inventory().item(slot).copyWithAmount(PAPER_PER_SEARCH);
    if (!payItemCost(player, "paper", unit, PAPER_PER_SEARCH, () -> takePaper(player, slot, PAPER_PER_SEARCH))) {
      return false;
    }
    if (hunger > 0 && !payHungerCost(player, "hunger", hunger, () -> {
      player.setFoodLevel(Math.max(0, player.foodLevel() - hunger));
      return true;
    })) {
      return false;
    }
    searchCooldowns.mark(id, getConfig().searchCooldownMillis);

    Id type = record.searchType(SEARCHABLE);
    Loc origin = player.location();
    UUID worldId = origin.world().uid();
    searching.put(id, new Search(worldId, type));
    searches.begin(player, origin, getConfig().searchRange, List.of(type),
        (StructureSense.Sensed found) -> foundOwned(player, worldId, type, found));
    player.sendMessage(TextFormat.AQUA + Text.key("adapt.tool.discovery.explorers_atlas.searching").withMarkup("structure", DiscoverySixthSense.structureName(null, type)).resolve());
    cue(player, MutationCue.WORK);
    return true;
  }

  private void foundOwned(PlayerRef player, UUID worldId, Id type, StructureSense.Sensed found) {
    AtlasRecord record = record(player);
    Search search = searching.get(player.uuid());
    if (record == null || search == null || !search.worldId().equals(worldId) || record.known(worldId, type, found.x(), found.z())) {
      return;
    }
    searching.remove(player.uuid());
    searches.cancel(player.uuid());
    Loc origin = player.location();
    record.lead(type, found.name(), worldId, origin.world().name(), found.x(), found.z(), found.bounds());
    save(player, record);
    double dx = found.x() - origin.x();
    double dz = found.z() - origin.z();
    player.sendMessage(TextFormat.AQUA + Text.key("adapt.tool.discovery.explorers_atlas.search_found").withMarkup("structure", TextFormat.WHITE + found.name() + TextFormat.AQUA).withMarkup("direction", TextFormat.YELLOW + DiscoverySixthSense.compassDirection(dx, dz) + TextFormat.AQUA).withMarkup("distance", TextFormat.WHITE + String.valueOf(Math.round(Math.sqrt(dx * dx + dz * dz))) + TextFormat.AQUA).resolve());
    cue(player, MutationCue.BOON);
  }

  private void finishSearchOwned(PlayerRef player) {
    UUID id = player.uuid();
    Search search = searching.get(id);
    if (search == null || searches.running(id, search.worldId())) {
      return;
    }
    searching.remove(id);
    player.sendMessage(TextFormat.GRAY + Text.key("adapt.tool.discovery.explorers_atlas.search_none").withMarkup("structure", DiscoverySixthSense.structureName(null, search.type())).withMarkup("range", Format.f(getConfig().searchRange, 0)).resolve());
  }

  String cover(PlayerRef player) {
    String cover = covers.get(player.uuid());
    return cover == null ? "" : cover;
  }

  /** The slot of the first plain paper the player carries (not a mutation tool, no name, lore or custom data), or -1. */
  private static int paperSlot(PlayerRef player) {
    ItemRef plain = AdaptApi.platform().host().items().create(PAPER, 1);
    List<ItemRef> contents = player.inventory().contents();
    for (int slot = 0; slot < contents.size(); slot++) {
      ItemRef item = contents.get(slot);
      if (item != null && !item.isEmpty() && PAPER.equals(item.type()) && !Mutations.isTool(item)
          && item.data().isEmpty() && item.isSimilar(plain)) {
        return slot;
      }
    }
    return -1;
  }

  /** Takes {@code amount} from the plain paper in {@code slot}; false when that slot no longer holds enough. */
  private static boolean takePaper(PlayerRef player, int slot, int amount) {
    PlayerInventoryRef inventory = player.inventory();
    ItemRef item = inventory.item(slot);
    if (item == null || !PAPER.equals(item.type()) || item.amount() < amount) {
      return false;
    }
    if (item.amount() == amount) {
      inventory.clear(slot);
    } else {
      inventory.setItem(slot, item.copyWithAmount(item.amount() - amount));
    }
    return true;
  }

  private void forget(UUID playerId) {
    sense.cancel(playerId);
    searches.cancel(playerId);
    structures.remove(playerId);
    focus.remove(playerId);
    records.remove(playerId);
    searching.remove(playerId);
    prompts.remove(playerId);
    covers.remove(playerId);
    showing.remove(playerId);
  }

  int pinsAllowed(int level) {
    return Math.max(0, getConfig().pinsAtFirstLevel + (Math.max(1, level) - 1) * getConfig().pinsPerLevel);
  }

  int waypointsAllowed(int level) {
    return Math.max(0, getConfig().waypointsAtFirstLevel << Math.max(0, Math.min(16, level - 1)));
  }

  boolean searchUnlocked(int level) {
    return level >= getConfig().searchLevel;
  }

  double getReadoutRange(int level) {
    return getConfig().readoutRangeBase + (level * getConfig().readoutRangePerLevel);
  }

  double getStructureRange(int level) {
    return DiscoverySixthSense.detectionRange(getLevelPercent(level), getConfig().structureRangeBase,
        getConfig().structureRangeFactor, getConfig().maxStructureRange);
  }

  private record Search(UUID worldId, Id type) {
  }

  private record Prompt(long expiresAt) {
  }

  @ConfigDescription("The creature you look at, nearby chests and spawners, and the nearest structure on one readout, with an Atlas of your finds.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Creature, chest and spawner range before level scaling, in blocks.", impact = "Higher values read farther targets.")
    double readoutRangeBase = 12;
    @ConfigDoc(value = "Creature, chest and spawner range added per level, in blocks.", impact = "Higher values widen the gap between levels.")
    double readoutRangePerLevel = 6;
    @ConfigDoc(value = "Cosine of the view cone for chests and spawners; -1 counts every direction.", impact = "Higher values only count containers closer to where you look.")
    double viewConeCos = -1;
    @ConfigDoc(value = "Ticks a chest or spawner you can see stays outlined after each readout.", impact = "Higher values keep outlines longer.")
    int outlineTicks = 16;
    @ConfigDoc(value = "Structure search range at level 1, in blocks.", impact = "Higher values find structures from farther away.")
    double structureRangeBase = 96;
    @ConfigDoc(value = "Structure search range gained across levels, in blocks.", impact = "Higher values reward higher levels with a wider search.")
    double structureRangeFactor = 404;
    @ConfigDoc(value = "Largest structure search range, in blocks; clamped to 16-500.", impact = "Lower values keep searches cheap.")
    double maxStructureRange = 500;
    @ConfigDoc(value = "Time between readout refreshes, in milliseconds.", impact = "Lower values update the readout more often at more cost.")
    long readoutIntervalMillis = 500;
    @ConfigDoc(value = "Time between structure searches, in milliseconds.", impact = "Lower values notice new structures sooner at more cost.")
    long structurePulseMillis = 4000;
    @ConfigDoc(value = "Time between Atlas checks of the biome and structures you stand in, in milliseconds.", impact = "Lower values add finds sooner at more cost.")
    long recordIntervalMillis = 1500;
    @ConfigDoc(value = "Most structures one Atlas keeps; past it the oldest unpinned one is dropped.", impact = "Higher values keep more finds in player data.")
    int maxStructures = 256;
    @ConfigDoc(value = "Pins at level 1.", impact = "Higher values show more entries on screen.")
    int pinsAtFirstLevel = 1;
    @ConfigDoc(value = "Pins added per level after the first.", impact = "Higher values reward higher levels with more pins.")
    int pinsPerLevel = 2;
    @ConfigDoc(value = "Waypoints at level 1; doubles each level.", impact = "Higher values let players name more places.")
    int waypointsAtFirstLevel = 5;
    @ConfigDoc(value = "Level that unlocks the unfound-structure search.", impact = "Lower values unlock the search sooner.")
    int searchLevel = 3;
    @ConfigDoc(value = "Unfound-structure search radius, in blocks. Only chunks that were already generated are read.", impact = "Higher values find farther structures but read more chunk data.")
    double searchRange = 2000;
    @ConfigDoc(value = "Cooldown between unfound-structure searches, in milliseconds.", impact = "Lower values allow more searches.")
    long searchCooldownMillis = 60000;
    @ConfigDoc(value = "Hunger each unfound-structure search costs, besides one paper.", impact = "Higher values make searches cost more food.")
    int searchHunger = 3;
  }
}
