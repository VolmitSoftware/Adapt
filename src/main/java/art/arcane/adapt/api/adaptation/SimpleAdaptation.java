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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public abstract class SimpleAdaptation<T> extends TickedObject implements Adaptation<T> {
  private int maxLevel;
  private int initialCost;
  private int baseCost;
  private double costFactor;
  private String displayName;
  private Skill<?> skill;
  private String description;
  private Material icon;
  private String name;
  private List<AdaptAdvancement> cachedAdvancements;
  private List<AdaptRecipe> recipes;
  private List<BrewingRecipe> brewingRecipes;
  private KList<AdaptStatTracker> statTrackers;
  private Class<T> configType;
  private volatile T config;
  private String localizationKey;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private volatile String localizedDescription;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private volatile String localizedDisplayName;

  public SimpleAdaptation(String name) {
    super("adaptations", UUID.randomUUID() + "-" + name, 1000);
    cachedAdvancements = new ArrayList<>();
    recipes = new ArrayList<>();
    brewingRecipes = new ArrayList<>();
    statTrackers = new KList<>();
    setMaxLevel(5);
    setCostFactor(0.45);
    setBaseCost(4);
    setIcon(Material.PAPER);
    setInitialCost(2);
    this.name = name;
    this.localizationKey = deriveLocalizationKey(name);
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
    return Adapt.instance.getDataFile("adapt", "adaptations", getName() + ".toml");
  }

  protected File getLegacyConfigFile() {
    return Adapt.instance.getDataFile("adapt", "adaptations", getName() + ".json");
  }

  protected T createDefaultConfig() {
    try {
      return getConfigurationClass().getConstructor().newInstance();
    } catch (Throwable e) {
      throw new IllegalStateException("Failed to create default config for adaptation " + getName(), e);
    }
  }

  public synchronized boolean reloadConfigFromDisk(boolean announce) {
    if (getConfigurationClass() == null) {
      return false;
    }

    T previous = config;
    File file = getConfigFile();
    try {
      T loaded = loadConfig(file, previous == null ? createDefaultConfig() : previous, previous == null);
      config = loaded;
      applySharedConfigValues(loaded);
      onConfigReload(previous, loaded);
      if (announce) {
        Adapt.info("Hotloaded " + file.getPath());
      }
      return true;
    } catch (Throwable e) {
      Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
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
      Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
      return false;
    }
  }

  private T loadConfig(File file, T fallback, boolean overwriteOnReadFailure) throws IOException {
    return ConfigFileSupport.load(
        file,
        getLegacyConfigFile(),
        getConfigurationClass(),
        fallback,
        overwriteOnReadFailure,
        "adaptation:" + getName(),
        "Created missing adaptation config [adapt/adaptations/" + getName() + ".toml] from defaults."
    );
  }

  private void applySharedConfigValues(T currentConfig) {
    applyIntField(currentConfig, "baseCost", this::setBaseCost);
    applyIntField(currentConfig, "initialCost", this::setInitialCost);
    applyIntField(currentConfig, "maxLevel", this::setMaxLevel);
    applyLongField(currentConfig, "setInterval", this::setInterval);
  }

  protected void onConfigReload(T previousConfig, T newConfig) {
    applyDoubleField(newConfig, "costFactor", this::setCostFactor);
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
      Adapt.verbose("Failed reading config field '" + fieldName + "' for adaptation " + getName());
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
        applySharedConfigValues(local);
        onConfigReload(null, local);
        config = local;
        Adapt.warn("Falling back to in-memory defaults for adaptation config " + getName() + ".");
      }
    }

    return local;
  }

  public void registerRecipe(AdaptRecipe r) {
    recipes.add(r);
  }

  public void registerBrewingRecipe(BrewingRecipe r) {
    brewingRecipes.add(r);
  }

  @Override
  public String getDisplayName() {
    try {
      String shownName = displayName;
      if (shownName == null) {
        shownName = localizedDisplayName;
        if (shownName == null) {
          shownName = resolveLocalized(localizationKey == null ? null : localizationKey + ".name", null);
          localizedDisplayName = shownName;
        }
      }
      return shownName == null ? Adaptation.super.getDisplayName() : (C.RESET + "" + C.BOLD + getSkill().getColor().toString() + shownName);
    } catch (Exception ignored) {
      Adapt.verbose("Failed to get display name for " + getName());
      return null;
    }
  }

  @Override
  public String getDescription() {
    String explicit = description;
    if (explicit != null) {
      return explicit;
    }

    String localized = localizedDescription;
    if (localized == null) {
      localized = resolveLocalized(localizationKey == null ? null : localizationKey + ".description", "No Description Provided");
      localizedDescription = localized;
    }
    return localized;
  }

  public void setLocalizationKey(String localizationKey) {
    this.localizationKey = localizationKey;
    this.localizedDescription = null;
    this.localizedDisplayName = null;
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
        .description(getDescription() + ". " + Localizer.dLocalize("snippets.gui.unlock_this_by_clicking") + " " + AdaptConfig.get().adaptActivatorBlockName)
        .icon(getIcon())
        .children(a)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build();
  }

  protected FxEmitter fx(Location location, FxPriority priority) {
    return Fx.now(this, location, priority);
  }

  protected FxEmitter fx(Entity entity, FxPriority priority) {
    return Fx.now(this, entity, priority);
  }

  protected double applyPlayerHealthLoss(Player player, double amount) {
    if (player == null || !Double.isFinite(amount) || amount <= 0D || player.isDead()) {
      return 0D;
    }

    double healthBefore = Math.max(0D, player.getHealth());
    double healthAfter = healthAfterLoss(healthBefore, amount);
    if (healthAfter >= healthBefore) {
      return 0D;
    }

    player.setHealth(healthAfter);
    fx(player, FxPriority.TRAIL)
        .particle(Particle.DAMAGE_INDICATOR, 3, 0, 1.0D, 0, 0.18D, 0.01D)
        .sound(Sound.ENTITY_PLAYER_HURT, 0.35F, 1.0F);
    return healthBefore - healthAfter;
  }

  static double healthAfterLoss(double health, double amount) {
    if (!Double.isFinite(health) || !Double.isFinite(amount) || health <= 0D || amount <= 0D) {
      return Math.max(0D, Double.isFinite(health) ? health : 0D);
    }
    return Math.max(0D, health - amount);
  }

  protected FxTimeline timeline(Location location) {
    return FxTimeline.at(this, location);
  }

  protected FxTimeline timeline(Entity entity) {
    return FxTimeline.follow(this, entity);
  }

  protected void sfx(Location location, Sound sound, float volume, float pitch) {
    Fx.now(this, location, FxPriority.GAMEPLAY).sound(sound, volume, pitch);
  }

  protected boolean grantOnce(Player p, String advancementKey) {
    if (!AdaptConfig.get().isAdvancements()) {
      return false;
    }

    AdaptPlayer adaptPlayer = getPlayer(p);
    if (adaptPlayer == null || adaptPlayer.getData().isGranted(advancementKey)) {
      return false;
    }

    return adaptPlayer.getAdvancementHandler().grant(advancementKey);
  }

  protected void addStat(Player p, String stat, double amount) {
    getPlayer(p).getData().addStat(stat, amount);
  }

  protected void statLore(Element v, Object value, int loreIndex) {
    statLore(v, C.GREEN, "+ ", value, loreIndex);
  }

  protected void statLore(Element v, C prefixColor, String prefix, Object value, int loreIndex) {
    v.addLore(prefixColor + prefix + formatStatValue(value) + C.GRAY + " " + Localizer.dLocalize(localizationKey + ".lore" + loreIndex));
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

    String resolved = Localizer.dLocalize(key);
    if (resolved == null || resolved.equals(key)) {
      return fallback;
    }

    return resolved;
  }
}
