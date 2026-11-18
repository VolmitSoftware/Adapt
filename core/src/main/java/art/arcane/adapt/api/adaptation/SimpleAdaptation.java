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

package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Getter
@Setter
public abstract class SimpleAdaptation<T> extends TickedObject implements Adaptation<T> {
  private static final Id PLAYER_ATTACK_DAMAGE = Id.minecraft("player_attack");
  private static final ThreadLocal<UUID> HEALTH_COST_TARGET = new ThreadLocal<>();
  private static final Id ICON_PAPER = Id.minecraft("paper");

  private int maxLevel;
  private int initialCost;
  private int baseCost;
  private double costFactor;
  private TextKey displayNameKey;
  private Skill<?> skill;
  private TextKey descriptionKey;
  private Id icon;
  private String name;
  private List<AdaptAdvancement> cachedAdvancements;
  private List<RecipeSpec> recipes;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private final List<BrewingRecipe> brewingRecipes = new ArrayList<>();
  private KList<AdaptStatTracker> statTrackers;
  private Class<T> configType;
  private volatile T config;
  private String localizationKey;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private volatile List<AdaptationRequirement> requirements = List.of();
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private long cachedDemandRevision = Long.MIN_VALUE;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private boolean cachedDemand;

  public SimpleAdaptation(String name) {
    super("adaptations", UUID.randomUUID() + "-" + name, 1000);
    cachedAdvancements = new ArrayList<>();
    recipes = new ArrayList<>();
    statTrackers = new KList<>();
    setMaxLevel(5);
    setCostFactor(0.45);
    setBaseCost(4);
    setIcon(ICON_PAPER);
    setInitialCost(2);
    this.name = name;
    this.localizationKey = deriveLocalizationKey(name);
  }

  public void setMaxLevel(int maxLevel) {
    this.maxLevel = Math.max(1, maxLevel);
  }

  public void setInitialCost(int initialCost) {
    this.initialCost = Math.max(0, initialCost);
  }

  public void setBaseCost(int baseCost) {
    this.baseCost = Math.max(0, baseCost);
  }

  public void setCostFactor(double costFactor) {
    this.costFactor = Double.isFinite(costFactor) ? Math.max(0D, costFactor) : 0D;
  }

  @Override
  public boolean hasTickDemand() {
    if (!usesLearnerBoundTicking() || isBursting()) {
      return true;
    }

    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    if (server == null) {
      return true;
    }

    long revision = server.getLearnerIndexRevision();
    if (revision != cachedDemandRevision) {
      cachedDemand = server.hasOnlineLearner(getName());
      cachedDemandRevision = revision;
    }

    return cachedDemand;
  }

  protected boolean usesLearnerBoundTicking() {
    return false;
  }

  @Override
  public Class<T> getConfigurationClass() {
    return configType;
  }

  @Override
  public void registerConfiguration(Class<T> type) {
    this.configType = type;
  }

  protected File getConfigFile() {
    return AdaptEnvironment.dataFile("adaptations", getName() + ".toml");
  }

  protected T createDefaultConfig() {
    try {
      return getConfigurationClass().getConstructor().newInstance();
    } catch (Throwable e) {
      throw new IllegalStateException("Failed to create default config for adaptation " + getName(), e);
    }
  }

  public synchronized boolean reloadConfigFromDisk(boolean announce) {
    return reloadConfigFromDisk(announce, config == null);
  }

  public synchronized boolean reloadConfigFromDiskPassive(boolean announce) {
    return reloadConfigFromDisk(announce, false);
  }

  private boolean reloadConfigFromDisk(boolean announce, boolean overwriteOnReadFailure) {
    if (getConfigurationClass() == null) {
      return false;
    }

    T previous = config;
    File file = getConfigFile();
    try {
      T loaded = loadConfig(file, previous == null ? createDefaultConfig() : previous, overwriteOnReadFailure);
      config = loaded;
      applySharedConfigValues(loaded);
      onConfigReload(previous, loaded);
      if (previous != null) {
        PlayerPreferences.reconcileOnline(this);
      }
      if (announce) {
        AdaptLog.info("Hotloaded " + file.getPath());
      }
      return true;
    } catch (Throwable e) {
      AdaptLog.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
      AdaptLog.error(e);
      return false;
    }
  }

  public synchronized boolean reloadConfigSnapshot(String raw, File sourceFile, boolean announce) {
    if (getConfigurationClass() == null) {
      return false;
    }

    T previous = config;
    try {
      T loaded = ConfigFileSupport.parseSnapshot(
          raw,
          sourceFile,
          getConfigurationClass(),
          "adaptation:" + getName(),
          this::normalizeConfiguration
      );
      config = loaded;
      applySharedConfigValues(loaded);
      onConfigReload(previous, loaded);
      if (previous != null) {
        PlayerPreferences.reconcileOnline(this);
      }
      if (announce) {
        AdaptLog.info("Hotloaded " + sourceFile.getPath());
      }
      return true;
    } catch (Throwable error) {
      AdaptLog.warn("Skipped hotload for " + sourceFile.getPath() + " due to invalid config: " + error.getMessage());
      AdaptLog.error(error);
      return false;
    }
  }

  public synchronized boolean validateConfigFromDisk() {
    if (getConfigurationClass() == null) {
      return false;
    }

    File file = getConfigFile();
    try {
      loadConfig(file, createDefaultConfig(), false);
      return true;
    } catch (Throwable e) {
      AdaptLog.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
      return false;
    }
  }

  public synchronized boolean writeDefaultConfig() {
    if (getConfigurationClass() == null) {
      return false;
    }

    File file = getConfigFile();
    try {
      ConfigFileSupport.writeDefaults(file, createDefaultConfig(), "adaptation:" + getName(), this::normalizeConfiguration);
      return true;
    } catch (Throwable e) {
      AdaptLog.warn("Failed to write default config " + file.getPath() + ": " + e.getMessage());
      return false;
    }
  }

  private T loadConfig(File file, T fallback, boolean overwriteOnReadFailure) throws IOException {
    return ConfigFileSupport.load(
        file,
        null,
        getConfigurationClass(),
        fallback,
        overwriteOnReadFailure,
        "adaptation:" + getName(),
        "Created missing adaptation config [adaptations/" + getName() + ".toml] from defaults.",
        this::normalizeConfiguration,
        shouldCanonicalizeConfigOnLoad()
    );
  }

  private void applySharedConfigValues(T currentConfig) {
    applyIntField(currentConfig, "baseCost", this::setBaseCost);
    applyIntField(currentConfig, "initialCost", this::setInitialCost);
    applyIntField(currentConfig, "maxLevel", this::setMaxLevel);
    applyLongField(currentConfig, "setInterval", this::setInterval);
    requirements = currentConfig instanceof AdaptationConfig shared
        ? AdaptationRequirement.parseAll(getName(), shared.requires)
        : List.of();
  }

  protected void onConfigReload(T previousConfig, T newConfig) {
    applyDoubleField(newConfig, "costFactor", this::setCostFactor);
  }

  protected void normalizeLoadedConfig(T loadedConfig) {
  }

  private void normalizeConfiguration(T loadedConfig) {
    normalizeLoadedConfig(loadedConfig);
    if (loadedConfig instanceof AdaptationConfig adaptationConfig) {
      PlayerPreferences.validate(this, adaptationConfig);
    }
  }

  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  private void applyIntField(T source, String fieldName, java.util.function.IntConsumer consumer) {
    Number number = getNumericField(source, fieldName);
    if (number != null) {
      consumer.accept(number.intValue());
    }
  }

  private void applyLongField(T source, String fieldName, java.util.function.LongConsumer consumer) {
    Number number = getNumericField(source, fieldName);
    if (number != null) {
      consumer.accept(number.longValue());
    }
  }

  private void applyDoubleField(T source, String fieldName, java.util.function.DoubleConsumer consumer) {
    Number number = getNumericField(source, fieldName);
    if (number != null) {
      consumer.accept(number.doubleValue());
    }
  }

  private Number getNumericField(T source, String fieldName) {
    Field f = getField(source.getClass(), fieldName);
    if (f == null) {
      return null;
    }

    try {
      f.setAccessible(true);
      Object value = f.get(source);
      if (value instanceof Number number) {
        return number;
      }
    } catch (Throwable ignored) {
      AdaptLog.verbose("Failed reading config field '" + fieldName + "' for adaptation " + getName());
    }

    return null;
  }

  private Field getField(Class<?> type, String name) {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        current = current.getSuperclass();
      }
    }

    return null;
  }

  @Override
  public T getConfig() {
    T local = config;
    if (local != null) {
      return local;
    }

    synchronized (this) {
      local = config;
      if (local != null) {
        return local;
      }

      boolean loaded = reloadConfigFromDisk(false);
      local = config;
      if (!loaded || local == null) {
        local = createDefaultConfig();
        normalizeConfiguration(local);
        applySharedConfigValues(local);
        onConfigReload(null, local);
        config = local;
        AdaptLog.warn("Falling back to in-memory defaults for adaptation config " + getName() + ".");
      }
    }

    return local;
  }

  public void registerRecipe(RecipeSpec r) {
    recipes.add(r);
  }

  public void registerBrewingRecipe(BrewingRecipe r) {
    brewingRecipes.add(r);
  }

  @Override
  public List<BrewingRecipe> getBrewingRecipes() {
    return Collections.unmodifiableList(brewingRecipes);
  }

  public boolean isBrewingRecipe(String recipeId) {
    for (BrewingRecipe recipe : brewingRecipes) {
      if (recipe.getId().equals(recipeId)) {
        return true;
      }
    }
    return false;
  }

  public int ownerLevel(UUID owner) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player == null ? 0 : getLevel(player);
  }

  @Override
  public String getDisplayName() {
    try {
      String shownName = displayNameKey == null
          ? resolveLocalized(localizationKey == null ? null : localizationKey + ".name", null)
          : AdaptLanguage.text(displayNameKey);
      return shownName == null ? Adaptation.super.getDisplayName() : (C.RESET + "" + C.BOLD + getSkill().getColor().toString() + shownName);
    } catch (Exception ignored) {
      AdaptLog.verbose("Failed to get display name for " + getName());
      return null;
    }
  }

  @Override
  public String getDescription() {
    return descriptionKey == null
        ? resolveLocalized(
            localizationKey == null ? null : localizationKey + ".description",
            AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED)
        )
        : AdaptLanguage.text(descriptionKey);
  }

  public void setLocalizationKey(String localizationKey) {
    this.localizationKey = localizationKey;
  }

  public void setDisplayNameKey(TextKey displayNameKey) {
    this.displayNameKey = displayNameKey;
  }

  public void setDescriptionKey(TextKey descriptionKey) {
    this.descriptionKey = descriptionKey;
  }

  @Override
  public boolean isEnabled() {
    if (getConfigurationClass() == null) {
      return true;
    }

    T current = getConfig();
    if (current instanceof AdaptationConfig shared) {
      return shared.enabled;
    }
    return true;
  }

  @Override
  public List<AdaptationRequirement> getRequirements() {
    if (getConfigurationClass() == null) {
      return List.of();
    }

    getConfig();
    return requirements;
  }

  @Override
  public boolean isPermanent() {
    if (getConfigurationClass() == null) {
      return false;
    }

    T current = getConfig();
    if (current instanceof AdaptationConfig shared) {
      return shared.permanent;
    }
    return false;
  }

  public void registerStatTracker(AdaptStatTracker tracker) {
    statTrackers.add(tracker);
  }

  public KList<AdaptStatTracker> getStatTrackers() {
    return statTrackers;
  }

  public void registerAdvancement(AdaptAdvancement a) {
    cachedAdvancements.add(a);
  }

  protected void registerAdvancementSpec(AdvancementSpec spec) {
    if (spec == null) {
      return;
    }

    registerAdvancement(spec.toAdvancement());
  }

  protected void registerMilestone(AdvancementSpec spec, String stat, double goal, double reward) {
    if (spec == null) {
      return;
    }

    registerAdvancementSpec(spec);
    registerStatTracker(spec.statTracker(stat, goal, reward));
  }

  protected void registerMilestone(String advancementKey, String stat, double goal, double reward) {
    registerStatTracker(AdaptStatTracker.builder()
        .advancement(advancementKey)
        .stat(stat)
        .goal(goal)
        .reward(reward)
        .build());
  }

  @Override
  public void onRegisterAdvancements(List<AdaptAdvancement> advancements) {
    advancements.addAll(cachedAdvancements);
  }

  public AdaptAdvancement buildAdvancements() {
    List<AdaptAdvancement> a = new ArrayList<>();
    onRegisterAdvancements(a);

    return AdaptAdvancement.builder()
        .key("adaptation_" + getName())
        .title(C.WHITE + "[     " + getDisplayName() + C.WHITE + "     ]")
        .description(AdaptLanguage.text(
            RuntimeMessages.ADVANCEMENT_UNLOCK,
            trusted("description", getDescription()),
            trusted("instruction", AdaptLanguage.text(SnippetsMessages.GUI_UNLOCK_THIS_BY_CLICKING)),
            untrusted("block", AdaptConfig.get().adaptActivatorBlockName)
        ))
        .icon(getIcon())
        .children(a)
        .visibility(AdvancementVisibility.VANILLA)
        .build();
  }

  protected FxEmitter fx(Loc location, FxPriority priority) {
    return Fx.now(this, location, priority);
  }

  protected FxEmitter fx(EntityRef entity, FxPriority priority) {
    return Fx.now(this, entity, priority);
  }

  protected void applyPlayerDamage(PlayerRef player, double amount) {
    if (player == null || !Double.isFinite(amount) || amount <= 0D || player.isDead()) {
      return;
    }

    payHealthCost(player, "health", (int) Math.ceil(amount), () -> {
      dispatchPlayerDamage(player, amount);
      return true;
    });
  }

  static void dispatchPlayerDamage(PlayerRef player, double amount) {
    UUID previous = HEALTH_COST_TARGET.get();
    HEALTH_COST_TARGET.set(player.uuid());
    try {
      player.damage(amount, PLAYER_ATTACK_DAMAGE);
    } finally {
      if (previous == null) {
        HEALTH_COST_TARGET.remove();
      } else {
        HEALTH_COST_TARGET.set(previous);
      }
    }
  }

  protected FxTimeline timeline(Loc location) {
    return FxTimeline.at(this, location);
  }

  protected FxTimeline timeline(EntityRef entity) {
    return FxTimeline.follow(this, entity);
  }

  protected void sfx(Loc location, Id sound, float volume, float pitch) {
    Fx.now(this, location, FxPriority.GAMEPLAY).sound(sound, volume, pitch);
  }

  protected boolean grantOnce(PlayerRef p, String advancementKey) {
    if (!AdaptConfig.get().isAdvancements()) {
      return false;
    }

    AdaptPlayer adaptPlayer = readyPlayer(p);
    if (adaptPlayer == null || adaptPlayer.getData().isGranted(advancementKey)) {
      return false;
    }

    return adaptPlayer.getAdvancementHandler().grant(advancementKey);
  }

  protected void addStat(PlayerRef p, String stat, double amount) {
    AdaptPlayer adaptPlayer = readyPlayer(p);
    if (adaptPlayer == null) {
      return;
    }
    adaptPlayer.getData().addStat(stat, amount);
  }

  protected double statOf(PlayerRef p, String stat) {
    AdaptPlayer adaptPlayer = readyPlayer(p);
    return adaptPlayer == null ? 0D : adaptPlayer.getData().getStat(stat);
  }

  private AdaptPlayer readyPlayer(PlayerRef player) {
    if (player == null) {
      return null;
    }
    AdaptPlayer adaptPlayer = getServer().getOnlineAdaptPlayer(player.uuid());
    return adaptPlayer != null && adaptPlayer.isRuntimeReady()
        && AdaptationRuntimeGuards.samePlayer(adaptPlayer.getPlayer(), player)
        ? adaptPlayer
        : null;
  }

  protected void statLore(StatLines v, Object value, int loreIndex) {
    statLore(v, C.GREEN, "+ ", value, loreIndex);
  }

  protected void statLore(StatLines v, C prefixColor, String prefix, Object value, int loreIndex) {
    statLore(v, prefixColor, prefix, value, C.GRAY, loreIndex);
  }

  protected void statLore(StatLines v, C prefixColor, String prefix, Object value, C labelColor, int loreIndex) {
    MessageKey loreKey = AdaptMessages.require(localizationKey + ".lore" + loreIndex);
    statLore(v, prefixColor, prefix, value, labelColor, loreKey);
  }

  protected void statLore(StatLines v, C prefixColor, String prefix, Object value, MessageKey loreKey) {
    statLore(v, prefixColor, prefix, value, C.GRAY, loreKey);
  }

  protected void statLore(StatLines v, C prefixColor, String prefix, Object value, C labelColor, MessageKey loreKey) {
    v.addLore(AdaptLanguage.text(
        RuntimeMessages.STAT_LORE,
        trusted("prefix", prefixColor + prefix),
        trusted("value", formatStatValue(value)),
        trusted("separator", labelColor + " "),
        trusted("label", AdaptLanguage.text(loreKey))
    ));
  }

  protected static void unlockLore(StatLines v, TextKey key) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(key));
  }

  private static String formatStatValue(Object value) {
    if (value instanceof Double d) {
      return Form.f(d, 2);
    }

    if (value instanceof Float f) {
      return Form.f(f, 2);
    }

    return String.valueOf(value);
  }

  protected <V> Map<UUID, V> playerState() {
    return PlayerStateRegistry.newPlayerMap();
  }

  protected Cooldowns cooldowns() {
    return PlayerStateRegistry.newCooldowns();
  }

  @Override
  public final boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  private static String deriveLocalizationKey(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }

    int split = name.indexOf('-');
    if (split < 0) {
      return name;
    }

    return name.substring(0, split) + "." + name.substring(split + 1).replace('-', '_');
  }

  private static String resolveLocalized(String key, String fallback) {
    if (key == null || key.isEmpty()) {
      return fallback;
    }

    String resolved = AdaptLanguage.text(AdaptMessages.require(key));
    if (resolved == null || resolved.isBlank()) {
      return fallback;
    }

    return resolved;
  }

  public static final class PlayerDamageListener {
    @Handle(priority = EventPriority.HIGHEST)
    public void on(EntityKnockbackEvent event) {
      UUID target = HEALTH_COST_TARGET.get();
      if (target != null
          && event.cause() == EntityKnockbackEvent.Cause.DAMAGE
          && target.equals(event.entity().uuid())) {
        event.setCancelled(true);
      }
    }
  }
}
