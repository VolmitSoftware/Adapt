/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.engine.world;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.engine.addon.DeclaredData;
import art.arcane.adapt.engine.addon.EnginePlayerSection;
import java.util.concurrent.ConcurrentHashMap;

import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.volmlib.util.localization.LanguageAudience;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.client.server.hud.ToastNotification;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.engine.adaptation.AdaptationRequirements;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.MutationNotice;
import art.arcane.adapt.engine.mutation.MutationOrigin;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.notification.ActionBarNotification;
import art.arcane.adapt.engine.notification.Notification;
import art.arcane.adapt.engine.notification.SoundNotification;
import art.arcane.adapt.engine.notification.TitleNotification;
import art.arcane.adapt.engine.preference.PlayerPreferenceData;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.xp.CombatXpLedger;
import art.arcane.adapt.engine.xp.Curves;
import art.arcane.adapt.engine.xp.ItemTransferXpLedger;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XPMultiplier;
import art.arcane.adapt.engine.xp.XpEarnings;
import art.arcane.adapt.engine.xp.XpNovelty;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

@Data
@NoArgsConstructor
public class PlayerData {
  private static final Comparator<TrimCandidate> TRIM_ORDER = Comparator
      .comparing(TrimCandidate::required)
      .thenComparingInt((TrimCandidate candidate) -> candidate.held().level())
      .thenComparing((TrimCandidate candidate) -> candidate.held().line())
      .thenComparing((TrimCandidate candidate) -> candidate.held().adaptation().getId().toString());
  private static final IconRef MASTER_LEVEL_ICON = IconRef.item("minecraft:experience_bottle");
  private static final Map<String, String> LINE_KEYS = new ConcurrentHashMap<>();
  private static final Set<String> FIELD_NAMES = fieldNames();

  private final KMap<String, PlayerSkillLine> skillLines = new KMap<>();
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<SkillPair> mutations;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private Map<String, MutationOrigin> mutationOrigins;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private Boolean mutationNoticeSent;
  private KMap<String, Double> stats = new KMap<>();
  private String last = "none";
  private KSet<String> advancements = new KSet<>();
  private Discovery<String> seenBiomes = new Discovery<>();
  private Discovery<String> seenMobs = new Discovery<>();
  private Discovery<String> seenFoods = new Discovery<>();
  private Discovery<String> seenItems = new Discovery<>();
  private Discovery<String> seenRecipes = new Discovery<>();
  private Discovery<String> seenEnchants = new Discovery<>();
  private Discovery<String> seenWorlds = new Discovery<>();
  private Discovery<String> seenPeople = new Discovery<>();
  private Discovery<String> seenEnvironments = new Discovery<>();
  private Discovery<String> seenPotionEffects = new Discovery<>();
  private Discovery<String> seenBlocks = new Discovery<>();
  private ChunkDiscovery seenChunks = new ChunkDiscovery();
  private KList<XPMultiplier> multipliers = new KList<>();
  private long wisdom = 0;
  private double multiplier = 1D;
  private long lastLogin = 0;
  private long lastLogout = 0;
  private XpNovelty.State xpNovelty = new XpNovelty.State();
  private CombatXpLedger combatXp = new CombatXpLedger();
  private XpEarnings xpEarnings = new XpEarnings();
  private ItemTransferXpLedger itemTransferXp = new ItemTransferXpLedger();
  private double masterXp = 1;
  private double lastMasterXp = 0;
  private volatile boolean effectsEnabled = true;
  private String inspiredSkill = "";
  private long inspiredAssignedAt = 0;
  private PlayerPreferenceData preferences = new PlayerPreferenceData();
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private final transient Map<String, Object> sections = new HashMap<>();
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private final transient Map<String, JsonElement> savedSections = new HashMap<>();
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private transient volatile int regionPowerBonus;
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private transient volatile int regionGrantedCount;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private transient boolean inspiredPopupPending;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private transient volatile AdaptPlayer runtimeOwner;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private transient volatile MasterLevelCache masterLevelCache;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private transient volatile MasterLevelCache previousMasterLevelCache;

  public static PlayerData fromJson(String json) {
    JsonElement tree = JsonParser.parseString(json);
    PlayerData data = Json.NORMAL.fromJson(tree, PlayerData.class);
    if (data == null) {
      return null;
    }
    for (Map.Entry<String, JsonElement> entry : tree.getAsJsonObject().entrySet()) {
      if (!FIELD_NAMES.contains(entry.getKey())) {
        data.savedSections.put(entry.getKey(), entry.getValue());
      }
    }
    data.normalizeInspiredAssignment();
    data.stripRegionGrantedAdaptations();
    return data;
  }

  /**
   * The live value of an addon data section: the one already in use, else the saved one, else a fresh one. It is
   * written back with the rest of the player file under the section's name.
   */
  public <T> T section(PlayerSection<T> declared) {
    EnginePlayerSection<T> section = DeclaredData.issued(declared);
    synchronized (sections) {
      Object live = sections.get(section.name());
      if (live != null) {
        if (!section.type().isInstance(live)) {
          throw new IllegalStateException("Player section " + section.name() + " holds " + live.getClass().getName()
              + ", not " + section.type().getName());
        }
        return section.type().cast(live);
      }
      JsonElement saved = savedSections.get(section.name());
      T value;
      try {
        value = saved == null || saved.isJsonNull() ? null : Json.NORMAL.fromJson(saved, section.type());
      } catch (RuntimeException unreadable) {
        throw new IllegalStateException("Player section " + section.name() + " cannot be read as "
            + section.type().getName() + ": " + unreadable.getMessage(), unreadable);
      }
      savedSections.remove(section.name());
      if (value == null) {
        value = section.create();
      }
      sections.put(section.name(), value);
      return value;
    }
  }

  private static Set<String> fieldNames() {
    Set<String> names = new HashSet<>();
    for (Field field : PlayerData.class.getDeclaredFields()) {
      int modifiers = field.getModifiers();
      if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
        names.add(field.getName());
      }
    }
    return Set.copyOf(names);
  }

  public PlayerPreferenceData getPreferences() {
    if (preferences == null) {
      preferences = new PlayerPreferenceData();
    }
    return preferences;
  }

  public XpNovelty.State getXpNovelty() {
    if (xpNovelty == null) {
      xpNovelty = new XpNovelty.State();
    }
    return xpNovelty;
  }

  public CombatXpLedger getCombatXp() {
    if (combatXp == null) {
      combatXp = new CombatXpLedger();
    }
    return combatXp;
  }

  public XpEarnings getXpEarnings() {
    if (xpEarnings == null) {
      xpEarnings = new XpEarnings();
    }
    return xpEarnings;
  }

  public ItemTransferXpLedger getItemTransferXp() {
    if (itemTransferXp == null) {
      itemTransferXp = new ItemTransferXpLedger();
    }
    return itemTransferXp;
  }

  public void giveMasterXp(double xp) {
    masterXp += xp;
  }

  public void globalXPMultiplier(double v, long duration) {
    multipliers.add(new XPMultiplier(v, duration));
  }

  public void globalXPMultiplier(XPMultiplier multiplier) {
    multipliers.add(Objects.requireNonNull(multiplier));
  }

  public void removeGlobalXPMultipliers(String source) {
    Objects.requireNonNull(source);
    multipliers.removeIf(multiplier -> multiplier != null && source.equals(multiplier.getSource()));
  }

  public boolean isGranted(String advancement) {
    return advancements.contains(advancement);
  }

  public boolean ensureGranted(String advancement) {
    return advancements.add(advancement);
  }

  public double getStat(String key) {
    Double d = stats.get(key);
    return d == null ? 0 : d;
  }

  public void addStat(String key, double amt) {
    stats.merge(key, amt, Double::sum);
    AdaptPlayer owner = runtimeOwner;
    if (owner != null) {
      owner.onStatChanged(key);
    }
  }

  void bindRuntimeOwner(AdaptPlayer owner) {
    runtimeOwner = owner;
    for (PlayerSkillLine line : skillLines.values()) {
      if (line != null) {
        line.bindRuntimeOwner(owner);
      }
    }
  }

  void unbindRuntimeOwner(AdaptPlayer owner) {
    if (runtimeOwner == owner) {
      runtimeOwner = null;
    }
    for (PlayerSkillLine line : skillLines.values()) {
      if (line != null) {
        line.unbindRuntimeOwner(owner);
      }
    }
  }

  public void update(AdaptPlayer p) {
    multiplier = computeXpMultiplier(p == null ? null : p.getPlayer());
    pulseInspired(p);

    for (Map.Entry<String, PlayerSkillLine> entry : skillLines.entrySet()) {
      String lineId = entry.getKey();
      Skill<?> loadedSkill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(lineId);
      if (loadedSkill == null) {
        // Never prune unknown lines automatically; missing skills can be transient
        // during startup/reload or due temporary config disables.
        continue;
      }

      PlayerSkillLine lineData = entry.getValue();
      if (lineData == null) {
        skillLines.remove(lineId);
        continue;
      }

      if (lineData.getXp() == 0 && lineData.getKnowledge() == 0 && lineData.getPooledXp() == 0 && lineData.getAdaptations().isEmpty()) {
        skillLines.remove(lineId, lineData);
        lineData.unbindRuntimeOwner(runtimeOwner);
        refreshLearnedIndex();
        continue;
      }

      lineData.update(p, lineId, this);
    }

    int oldLevel = (int) previousMasterLevel();
    int level = (int) masterLevel();

    if (oldLevel != level) {
      setLastMasterXp(getMasterXp());
      notifyMasterLevel(p, level);
    }

    SkillRegistry registry = AdaptCore.get().getAdaptServer().getSkillRegistry();
    if (p != null && registry != null && !isMutationNoticeSent()) {
      MutationNotice.sendIfAvailable(p, registry.getSkills(), AdaptConfig.get().getMutations());
    }
  }

  /** Whether the player was already told that a mutation became available; it is said once per player. */
  public boolean isMutationNoticeSent() {
    return Boolean.TRUE.equals(mutationNoticeSent);
  }

  public void markMutationNoticeSent() {
    mutationNoticeSent = Boolean.TRUE;
  }

  private void notifyMasterLevel(AdaptPlayer player, int level) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.getPlayer() == null ? null : player.getPlayer().uuid())) {
      AdaptConfig config = AdaptConfig.get();
      boolean showPopup = config.isActionbarNotifyMasterLevel();
      boolean playSounds = config.isProgressionSoundsEnabled();
      if (!showPopup && !playSounds) {
        return;
      }

      KList<Notification> notifications = new KList<>();
      boolean clientToast = showPopup && player.getPlayer() != null && ClientToasts.capable(player.getPlayer().uuid());
      if (playSounds) {
        notifications.add(SoundNotification.builder()
            .sound(Sounds.BLOCK_ENCHANTMENT_TABLE_USE)
            .volume(0.5f)
            .pitch(0.54f)
            .group("lvl")
            .build());
        notifications.add(SoundNotification.builder()
            .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME)
            .volume(0.5f)
            .pitch(0.44f)
            .group("lvl")
            .build());
        notifications.add(SoundNotification.builder()
            .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME)
            .volume(0.5f)
            .pitch(0.74f)
            .group("lvl")
            .build());
        notifications.add(SoundNotification.builder()
            .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME)
            .volume(0.5f)
            .pitch(1.34f)
            .group("lvl")
            .build());
      }
      Notification title = TitleNotification.builder()
          .in(250)
          .stay(1450)
          .out(2250)
          .displayDurationMillis(config.getActionbarLevelDurationMillis())
          .group("lvl")
          .title("")
          .subtitle(masterLevelText(level))
          .build();
      Notification powerBar = ActionBarNotification.builder()
          .duration(450)
          .displayDurationMillis(config.getActionbarLevelDurationMillis())
          .group("power")
          .title(maxPowerText(level, config))
          .build();
      if (clientToast) {
        notifications.add(new ToastNotification(ClientToasts.toast(ToastKind.LEVEL_UP, MASTER_LEVEL_ICON,
            masterLevelText(level), maxPowerText(level, config), ""), "lvl", List.of(title, powerBar)));
      } else if (showPopup) {
        notifications.add(title);
      }
      if (!notifications.isEmpty()) {
        player.getNot().queue(notifications.toArray(new Notification[0]));
      }
      if (showPopup && !clientToast) {
        player.getActionBarNotifier().queue(powerBar);
      }
    }
  }

  private static String masterLevelText(int level) {
    return C.GOLD + AdaptLanguage.text(SnippetsMessages.GUI_LEVEL_VALUE, trusted("level", level));
  }

  private static String maxPowerText(int level, AdaptConfig config) {
    return C.GOLD + AdaptLanguage.text(
        RuntimeMessages.MAX_ABILITY_POWER,
        trusted("power", maxPowerFor(level, config.getMaxPowerScale()) + "" + C.GRAY)
    );
  }

  public double computeXpMultiplier(PlayerRef player) {
    double m = 1D;
    m += collectActivePlayerMultiplierBonus();
    m += collectGlobalMultiplierBonus();
    m *= resolvePermissionMultiplier(player);

    if (m <= 0) {
      m = 0.01;
    }

    if (m > 1000) {
      m = 1000;
    }

    return m;
  }

  private static double resolvePermissionMultiplier(PlayerRef player) {
    if (player == null) {
      return 1D;
    }

    AdaptConfig.PermissionXpMultipliers settings = AdaptConfig.get().getPermissionXpMultipliers();
    if (settings == null || !settings.isEnabled()) {
      return 1D;
    }

    Map<String, Double> nodes = settings.getMultipliers();
    if (nodes == null || nodes.isEmpty()) {
      return 1D;
    }

    boolean stack = settings.isStack();
    double resolved = 1D;
    boolean matched = false;
    for (Map.Entry<String, Double> entry : nodes.entrySet()) {
      String node = entry.getKey();
      Double value = entry.getValue();
      if (node == null || node.isBlank() || value == null || value <= 0D) {
        continue;
      }

      if (!player.hasPermission(node)) {
        continue;
      }

      if (stack) {
        resolved *= value;
        matched = true;
        continue;
      }

      if (!matched || value > resolved) {
        resolved = value;
        matched = true;
      }
    }

    return resolved;
  }

  private double collectActivePlayerMultiplierBonus() {
    double bonus = 0D;
    for (int i = multipliers.size() - 1; i >= 0; i--) {
      XPMultiplier active = multipliers.get(i);
      if (active == null || !active.isActive()) {
        multipliers.remove(i);
        continue;
      }
      bonus += active.getMultiplier();
    }
    return bonus;
  }

  private double collectGlobalMultiplierBonus() {
    double bonus = 0D;
    List<XPMultiplier> globalMultipliers = AdaptCore.get().getAdaptServer().getData().getMultipliers();
    for (int i = 0; i < globalMultipliers.size(); i++) {
      XPMultiplier active = globalMultipliers.get(i);
      if (active == null || !active.isActive()) {
        continue;
      }
      bonus += active.getMultiplier();
    }
    return bonus;
  }

  public int getAvailablePower() {
    return getMaxPower() - getUsedPower();
  }

  public boolean hasPowerAvailable() {
    return hasPowerAvailable(1);
  }

  public boolean hasPowerAvailable(int amount) {
    if (isDebugLearning()) {
      return true;
    }

    return getAvailablePower() >= amount;
  }

  private boolean isDebugLearning() {
    AdaptPlayer owner = runtimeOwner;
    return owner != null && owner.getPlayer() != null && AdaptDebugMode.isActive(owner.getPlayer().uuid());
  }

  public int getUsedPower() {
    return heldAdaptations().size();
  }

  /**
   * The adaptations that take a power slot: learned and paid for, registered in the skill of the line they are stored
   * in, enabled, holdable with the player's mutations, and permitted to the player while online, then each kept original
   * of a mutated line raised above its free level. Anything else stays stored but takes no slot.
   */
  public List<HeldAdaptation> heldAdaptations() {
    SkillRegistry registry = skillRegistry();
    if (registry == null) {
      return List.of();
    }
    PlayerRef player = onlinePlayer();
    List<HeldAdaptation> held = new ArrayList<>();
    for (Map.Entry<String, PlayerSkillLine> entry : skillLines.entrySet()) {
      PlayerSkillLine line = entry.getValue();
      Skill<?> skill = registry.getSkill(entry.getKey());
      if (line == null || skill == null || !skill.isEnabled()
          || (player != null && !skill.hasUsePermission(player))) {
        continue;
      }
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        PlayerAdaptation stored = line.getAdaptation(adaptation.getId().toString());
        if (stored != null && !stored.isRegionGranted() && stored.getLevel() > 0 && adaptation.isEnabled()
            && SkillUpgrades.holdable(adaptation, this)
            && (player == null || adaptation.hasUsePermission(player))) {
          held.add(new HeldAdaptation(entry.getKey(), adaptation, stored.getLevel()));
        }
      }
      if (skill instanceof MutatedSkill mutated) {
        for (Adaptation<?> kept : mutated.getKept()) {
          int raised = SkillUpgrades.raisedLevel(kept, this);
          if (raised > 0 && (player == null || kept.hasUsePermission(player))) {
            held.add(new HeldAdaptation(entry.getKey(), kept, raised));
          }
        }
      }
    }
    return held;
  }

  private static SkillRegistry skillRegistry() {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server == null ? null : server.getSkillRegistry();
  }

  /** The player this data is loaded for, or null for data with no runtime owner. */
  public PlayerRef ownerPlayer() {
    AdaptPlayer owner = runtimeOwner;
    return owner == null ? null : owner.getPlayer();
  }

  private PlayerRef onlinePlayer() {
    AdaptPlayer owner = runtimeOwner;
    PlayerRef player = owner == null ? null : owner.getPlayer();
    return player != null && player.isOnline() ? player : null;
  }

  public int getLevel() {
    return (int) masterLevel();
  }

  public int getMaxPower() {
    return Math.max(0, maxPowerFor(masterLevel(), AdaptConfig.get().getMaxPowerScale()) + regionPowerBonus);
  }

  /** Power earned at a master level, before region bonuses: floor(scale * sqrt(level)). */
  public static int maxPowerFor(double masterLevel, double scale) {
    if (!Double.isFinite(masterLevel) || masterLevel <= 0D || !Double.isFinite(scale) || scale <= 0D) {
      return 0;
    }
    return (int) Math.floor(scale * Math.sqrt(masterLevel));
  }

  public int stripRegionGrantedAdaptations() {
    int stripped = 0;
    for (PlayerSkillLine line : skillLines.values()) {
      if (line == null) {
        continue;
      }

      for (String adaptationName : line.getAdaptations().k()) {
        PlayerAdaptation adaptation = line.getAdaptations().get(adaptationName);
        if (adaptation == null || !adaptation.isRegionGranted()) {
          continue;
        }

        line.getAdaptations().remove(adaptationName);
        stripped++;
      }
    }

    regionGrantedCount = 0;
    if (stripped > 0) {
      refreshLearnedIndex();
    }
    return stripped;
  }

  /**
   * The line a skill's progress goes to, created when missing; null for an unknown skill and for a suspended mutated
   * line, one whose partner or lead is no longer registered.
   */
  public PlayerSkillLine getSkillLine(String requestedLine) {
    SkillRegistry registry = AdaptCore.get().getAdaptServer().getSkillRegistry();
    if (registry.getSkill(requestedLine) == null) {
      return null;
    }

    String skillLine = lineIdFor(requestedLine);
    if (!skillLine.equals(lineKey(requestedLine)) && registry.getSkill(skillLine) == null) {
      return null;
    }
    synchronized (skillLines) {
      try {
        PlayerSkillLine s = skillLines.get(skillLine);

        if (s != null) {
          return s;
        }
      } catch (Throwable e) {
        AdaptLog.error(e);
        AdaptLog.error("Failed to get skill line " + skillLine);
      }

      PlayerSkillLine s = new PlayerSkillLine();
      s.setLine(skillLine);
      s.bindRuntimeOwner(runtimeOwner);
      skillLines.put(skillLine, s);
      return s;
    }
  }

  public PlayerSkillLine getSkillLineNullable(String skillLine) {
    return skillLines.get(lineIdFor(skillLine));
  }

  /**
   * The line a skill's progress is stored in: the mutated line when the skill is a parent in one of the player's
   * mutations. A bare skill name is in the {@code adapt} namespace.
   */
  public String lineIdFor(String skill) {
    String key = lineKey(skill);
    SkillPair mutation = mutationOf(key);
    return mutation == null ? key : mutation.key();
  }

  /**
   * The key a skill's line is stored under: the id's string form, or a mutation's key. A bare name such as
   * {@code swords} is in the {@code adapt} namespace; anything else is returned unchanged.
   */
  public static String lineKey(String skill) {
    if (skill == null) {
      return null;
    }
    return LINE_KEYS.computeIfAbsent(skill, (String raw) -> {
      SkillPair mutation = PairKeys.parse(raw);
      Id id = mutation == null ? Id.tryParseAdapt(raw) : null;
      return mutation != null ? mutation.key() : id != null ? id.toString() : raw;
    });
  }

  /** The player's mutation the skill is a parent in, or null. */
  public SkillPair mutationOf(String skill) {
    Id id = mutations == null || skill == null ? null : Id.tryParseAdapt(skill);
    if (id == null) {
      return null;
    }
    for (SkillPair mutation : mutations) {
      if (mutation.involves(id)) {
        return mutation;
      }
    }
    return null;
  }

  /** The player's mutations, unmodifiable; the stored list is frozen on first read so later reads do not copy. */
  public List<SkillPair> getMutations() {
    List<SkillPair> current = mutations;
    if (current == null) {
      return List.of();
    }
    List<SkillPair> frozen = List.copyOf(current);
    if (frozen != current) {
      mutations = frozen;
    }
    return frozen;
  }

  /** Replaces both parent lines with the merged line and records the mutation with what its parents held. */
  public void installMutation(SkillPair mutation, PlayerSkillLine merged, MutationOrigin origin) {
    synchronized (skillLines) {
      unbindLine(skillLines.remove(mutation.lead().toString()));
      unbindLine(skillLines.remove(mutation.partner().toString()));
      merged.bindRuntimeOwner(runtimeOwner);
      skillLines.put(mutation.key(), merged);
    }
    List<SkillPair> updated = new ArrayList<>(getMutations());
    updated.add(mutation);
    mutations = List.copyOf(updated);
    Map<String, MutationOrigin> origins = mutationOrigins == null ? new HashMap<>() : new HashMap<>(mutationOrigins);
    origins.put(mutation.key(), origin);
    mutationOrigins = origins;
    refreshLearnedIndex();
  }

  /** What the parents held when the mutation was made, or null for a mutation saved before that was recorded. */
  public MutationOrigin mutationOrigin(SkillPair mutation) {
    Map<String, MutationOrigin> origins = mutationOrigins;
    return origins == null ? null : origins.get(mutation.key());
  }

  /** Replaces the mutated line with its two parent lines and forgets the mutation. */
  public void removeMutation(SkillPair mutation, PlayerSkillLine lead, PlayerSkillLine partner) {
    List<SkillPair> remaining = new ArrayList<>(getMutations());
    remaining.remove(mutation);
    mutations = remaining.isEmpty() ? null : List.copyOf(remaining);
    if (mutationOrigins != null) {
      Map<String, MutationOrigin> origins = new HashMap<>(mutationOrigins);
      origins.remove(mutation.key());
      mutationOrigins = origins.isEmpty() ? null : origins;
    }
    synchronized (skillLines) {
      unbindLine(skillLines.remove(mutation.key()));
      lead.bindRuntimeOwner(runtimeOwner);
      partner.bindRuntimeOwner(runtimeOwner);
      skillLines.put(mutation.lead().toString(), lead);
      skillLines.put(mutation.partner().toString(), partner);
    }
    refreshLearnedIndex();
  }

  /** Deletes a saved line whatever its content; false when there is none. */
  public boolean deleteLine(String line) {
    PlayerSkillLine removed;
    synchronized (skillLines) {
      removed = skillLines.remove(line);
    }
    if (removed == null) {
      return false;
    }
    unbindLine(removed);
    refreshLearnedIndex();
    return true;
  }

  /** Deletes one saved adaptation from a line without running anything it does on unlearn; false when absent. */
  public boolean deleteAdaptation(String line, String adaptation) {
    PlayerSkillLine stored = skillLines.get(line);
    if (stored == null || stored.getAdaptations().remove(adaptation) == null) {
      return false;
    }
    refreshLearnedIndex();
    return true;
  }

  private void unbindLine(PlayerSkillLine line) {
    if (line != null) {
      line.unbindRuntimeOwner(runtimeOwner);
    }
  }

  public void noteActivity(String currentSkill) {
    if (currentSkill == null || currentSkill.isBlank() || currentSkill.equals(last)) {
      return;
    }

    last = currentSkill;
    String inspiredCandidate = null;
    double inspiredPressure = 0.0D;
    for (Map.Entry<String, PlayerSkillLine> entry : skillLines.entrySet()) {
      String lineId = entry.getKey();
      PlayerSkillLine line = entry.getValue();
      if (line == null || lineId == null || lineId.isBlank() || currentSkill.equals(lineId)) {
        continue;
      }

      double recoveredPressure = line.recoverStaleness(System.currentTimeMillis());
      if (isBetterInspiredCandidate(lineId, recoveredPressure, inspiredCandidate, inspiredPressure)) {
        inspiredCandidate = lineId;
        inspiredPressure = recoveredPressure;
      }
    }

    assignInspiredCandidate(inspiredCandidate);
  }

  private void assignInspiredCandidate(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return;
    }

    AdaptConfig.XpIntegrity integrity = AdaptConfig.get().getXpIntegrity();
    if (integrity == null) {
      return;
    }

    long now = System.currentTimeMillis();
    long cooldownMillis = Math.max(0L, integrity.getInspiredCooldownMillis());
    if (!isInspiredCooldownComplete(now, cooldownMillis)) {
      return;
    }

    inspiredSkill = candidate;
    inspiredAssignedAt = now;
    inspiredPopupPending = integrity.isInspiredPopupEnabled();
  }

  private boolean isInspiredCooldownComplete(long now, long cooldownMillis) {
    if (inspiredAssignedAt <= 0) {
      return true;
    }

    long elapsed = now - inspiredAssignedAt;
    return elapsed >= 0 && elapsed >= cooldownMillis;
  }

  private boolean isBetterInspiredCandidate(String lineId, double pressure, String currentCandidate, double currentPressure) {
    if (pressure <= 0.0D) {
      return false;
    }
    if (currentCandidate == null) {
      return true;
    }

    int pressureComparison = Double.compare(pressure, currentPressure);
    return pressureComparison > 0 || (pressureComparison == 0 && lineId.compareTo(currentCandidate) < 0);
  }

  private void pulseInspired(AdaptPlayer p) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(p.getPlayer() == null ? null : p.getPlayer().uuid())) {
      if (!inspiredPopupPending) {
        return;
      }

      inspiredPopupPending = false;
      AdaptConfig.XpIntegrity integrity = AdaptConfig.get().getXpIntegrity();
      if (integrity == null || !integrity.isInspiredPopupEnabled() || inspiredSkill == null || inspiredSkill.isBlank()) {
        return;
      }

      Skill<?> skill = p.getServer().getSkillRegistry().getSkill(inspiredSkill);
      if (skill == null) {
        return;
      }

      p.getActionBarNotifier().queue(ActionBarNotification.builder()
          .duration(1250)
          .group("inspired")
          .title(skill.getDisplayName() + C.RESET + " " + C.GREEN + AdaptLanguage.text(SnippetsMessages.XP_INSPIRED))
          .build());
    }
  }

  private void normalizeInspiredAssignment() {
    inspiredSkill = inspiredSkill == null ? "" : inspiredSkill.trim();
    inspiredAssignedAt = Math.max(0L, inspiredAssignedAt);
    if (inspiredSkill.isEmpty()) {
      inspiredAssignedAt = 0;
    }
  }

  public void addWisdom() {
    wisdom++;
  }

  /** Master XP back to its start, and the inspired skill forgotten. */
  public void resetMasterXp() {
    masterXp = 1;
    lastMasterXp = 0;
    inspiredSkill = "";
    inspiredAssignedAt = 0;
    inspiredPopupPending = false;
  }

  public void clearStats() {
    stats.clear();
  }

  public void clearDiscoveries() {
    seenBiomes = new Discovery<>();
    seenMobs = new Discovery<>();
    seenFoods = new Discovery<>();
    seenItems = new Discovery<>();
    seenRecipes = new Discovery<>();
    seenEnchants = new Discovery<>();
    seenWorlds = new Discovery<>();
    seenPeople = new Discovery<>();
    seenEnvironments = new Discovery<>();
    seenPotionEffects = new Discovery<>();
    seenBlocks = new Discovery<>();
    seenChunks = new ChunkDiscovery();
  }

  public void pruneAdaptationsForPowerBudget() {
    if (isDebugLearning()) {
      return;
    }

    AdaptationRequirements.forfeitLevels(trimToPowerBudget(), this, ownerName());
  }

  private List<AdaptationRequirements.Lowering> trimToPowerBudget() {
    int usedPower = getUsedPower();
    int maxPower = getMaxPower();
    List<AdaptationRequirements.Lowering> removed = new ArrayList<>();

    while (usedPower > maxPower) {
      HeldAdaptation candidate = nextTrimCandidate();
      if (candidate == null) {
        break;
      }

      removed.add(new AdaptationRequirements.Lowering(candidate.adaptation(), candidate.level()));
      skillLines.get(candidate.line()).getAdaptations().remove(candidate.adaptation().getId().toString());
      usedPower -= 1;
    }
    refreshLearnedIndex();
    return List.copyOf(removed);
  }

  private HeldAdaptation nextTrimCandidate() {
    TrimCandidate best = null;
    for (HeldAdaptation held : heldAdaptations()) {
      boolean required = !AdaptationRequirements.dependents(held.adaptation(), this, 0).isEmpty();
      TrimCandidate candidate = new TrimCandidate(held, required);
      if (best == null || TRIM_ORDER.compare(candidate, best) < 0) {
        best = candidate;
      }
    }
    return best == null ? null : best.held();
  }


  private String ownerName() {
    AdaptPlayer owner = runtimeOwner;
    PlayerRef player = owner == null ? null : owner.getPlayer();
    return player == null ? "an offline player" : player.name();
  }

  private void refreshLearnedIndex() {
    AdaptPlayer owner = runtimeOwner;
    if (owner != null && owner.getServer() != null) {
      owner.getServer().refreshLearnedAdaptations(owner);
    }
  }

  private double masterLevel() {
    AdaptConfig config = AdaptConfig.get();
    Curves curve = config.getXpCurve();
    int maximumLevel = config.experienceMaxLevel;
    long xpBits = Double.doubleToLongBits(masterXp);
    MasterLevelCache cached = masterLevelCache;
    if (cached != null && cached.matches(xpBits, curve, maximumLevel)) {
      return cached.level();
    }

    double resolved = XP.getLevelForXp(masterXp);
    masterLevelCache = new MasterLevelCache(xpBits, curve, maximumLevel, resolved);
    return resolved;
  }

  private double previousMasterLevel() {
    AdaptConfig config = AdaptConfig.get();
    Curves curve = config.getXpCurve();
    int maximumLevel = config.experienceMaxLevel;
    long xpBits = Double.doubleToLongBits(lastMasterXp);
    MasterLevelCache cached = previousMasterLevelCache;
    if (cached != null && cached.matches(xpBits, curve, maximumLevel)) {
      return cached.level();
    }

    double resolved = XP.getLevelForXp(lastMasterXp);
    previousMasterLevelCache = new MasterLevelCache(xpBits, curve, maximumLevel, resolved);
    return resolved;
  }

  public String toJson(boolean raw) {
    synchronized (skillLines) {
      for (PlayerSkillLine line : skillLines.values()) {
        if (line != null) {
          line.flushXpPool(null);
        }
      }
      synchronized (getXpNovelty()) {
        synchronized (getCombatXp()) {
          synchronized (getXpEarnings()) {
            synchronized (getItemTransferXp()) {
              return (raw ? Json.NORMAL : Json.PRETTY).toJson(withSections());
            }
          }
        }
      }
    }
  }

  private JsonObject withSections() {
    JsonObject tree = Json.NORMAL.toJsonTree(this).getAsJsonObject();
    synchronized (sections) {
      for (Map.Entry<String, JsonElement> saved : savedSections.entrySet()) {
        tree.add(saved.getKey(), saved.getValue());
      }
      for (Map.Entry<String, Object> live : sections.entrySet()) {
        tree.add(live.getKey(), Json.NORMAL.toJsonTree(live.getValue()));
      }
    }
    return tree;
  }

  /** An adaptation taking a power slot, the skill line it is stored in, and its stored level. */
  public record HeldAdaptation(String line, Adaptation<?> adaptation, int level) {
  }

  private record TrimCandidate(HeldAdaptation held, boolean required) {
  }

  private record MasterLevelCache(long xpBits, Curves curve, int maximumLevel, double level) {
    private boolean matches(long candidateXpBits, Curves candidateCurve, int candidateMaximumLevel) {
      return xpBits == candidateXpBits && curve == candidateCurve && maximumLevel == candidateMaximumLevel;
    }
  }
}
