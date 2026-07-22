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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.util.common.format.AdventureCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;

@Getter
@Setter
public abstract class SimpleSkill<T> extends TickedObject implements Skill<T> {
  private final String name;
  private final SkillPresentation presentation;
  private C color;
  private String colorPrefix;
  private double minXp;
  private Material icon;
  private KList<Adaptation<?>> adaptations;
  private KList<AdaptStatTracker> statTrackers;
  private KList<AdaptAdvancement> cachedAdvancements;
  private String advancementBackground;
  private KList<AdaptRecipe> recipes;
  private Class<T> configType;
  private volatile T config;
  private final AtomicBoolean unregistered = new AtomicBoolean();

  public SimpleSkill(String name, SkillPresentation presentation) {
    super("skill", UUID.randomUUID() + "-skill-" + name, 50);
    statTrackers = new KList<>();
    recipes = new KList<>();
    cachedAdvancements = new KList<>();
    this.presentation = Objects.requireNonNull(presentation, "presentation");
    adaptations = new KList<>();
    setColor(C.WHITE);
    this.name = name;
    setIcon(Material.BOOK);
    setMinXp(100);
    setAdvancementBackground("minecraft:textures/block/deepslate_tiles.png");
  }

  @Override
  protected void onRuntimeActivated() {
    J.a(() -> {
      if (!isRuntimeRegistered()) {
        return;
      }
      J.attempt(this::getConfig);
      getAdaptations().forEach(i -> J.attempt(i::getConfig));
    }, 1);
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
    return Adapt.instance.getDataFile("adapt", "skills", getName() + ".toml");
  }

  protected File getLegacyConfigFile() {
    return Adapt.instance.getDataFile("adapt", "skills", getName() + ".json");
  }

  protected T createDefaultConfig() {
    try {
      return getConfigurationClass().getConstructor().newInstance();
    } catch (Throwable e) {
      throw new IllegalStateException("Failed to create default config for skill " + getName(), e);
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
        "skill:" + getName(),
        "Created missing skill config [adapt/skills/" + getName() + ".toml] from defaults.",
        this::normalizeLoadedConfig,
        shouldCanonicalizeConfigOnLoad()
    );
  }

  protected void normalizeLoadedConfig(T loadedConfig) {
  }

  protected boolean shouldCanonicalizeConfigOnLoad() {
    return false;
  }

  protected void onConfigReload(T previousConfig, T newConfig) {
    applyColorField(newConfig);
    applyDoubleField(newConfig, "minXp", this::setMinXp);
    Number interval = getNumericField(newConfig, "setInterval");
    if (interval != null) {
      setInterval(interval.longValue());
    }
  }

  private void applyDoubleField(T source, String fieldName, java.util.function.DoubleConsumer consumer) {
    Number number = getNumericField(source, fieldName);
    if (number != null) {
      consumer.accept(number.doubleValue());
    }
  }

  private void applyColorField(T source) {
    String configuredColor = getStringField(source, "skillColor");
    if (configuredColor == null || configuredColor.isBlank()) {
      return;
    }

    String prefix = resolveColorPrefix(configuredColor);
    if (prefix == null || prefix.isBlank()) {
      return;
    }

    colorPrefix = prefix;
    C resolvedColor = resolveLegacyColor(prefix);
    if (resolvedColor != null) {
      color = resolvedColor;
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
    } catch (Throwable ex) {
      Adapt.verbose("Failed reading config field '" + fieldName + "' for skill " + getName() + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }

    return null;
  }

  private String getStringField(T source, String fieldName) {
    Field f = getField(source.getClass(), fieldName);
    if (f == null) {
      return null;
    }

    try {
      f.setAccessible(true);
      Object value = f.get(source);
      if (value instanceof String stringValue) {
        return stringValue;
      }
    } catch (Throwable ex) {
      Adapt.verbose("Failed reading config field '" + fieldName + "' for skill " + getName() + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }

    return null;
  }

  private Field getField(Class<?> type, String name) {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException ex) {
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
        onConfigReload(null, local);
        config = local;
        Adapt.warn("Falling back to in-memory defaults for skill config " + getName() + ".");
      }
    }

    return local;
  }

  public void registerRecipe(AdaptRecipe r) {
    recipes.add(r);
  }

  public void registerAdvancement(AdaptAdvancement a) {
    cachedAdvancements.add(a);
  }

  protected void registerAdvancementSpec(AdvancementSpec spec) {
    if (spec != null) {
      registerAdvancement(spec.toAdvancement());
    }
  }

  protected void registerMilestone(AdvancementSpec spec, String stat, double goal, double reward) {
    if (spec == null) {
      return;
    }

    registerAdvancementSpec(spec);
    registerStatTracker(spec.statTracker(stat, goal, reward));
  }

  public boolean checkValidEntity(EntityType e) {
    if (!e.isAlive() || e.equals(EntityType.PARROT)) {
      return false;
    }
    return !ItemListings.getInvalidDamageableEntities().contains(e);
  }

  protected boolean shouldReturnForPlayer(Player p) {
    return SkillRuntimeGuards.shouldSkipPlayer(this, p);
  }

  protected void shouldReturnForPlayer(Player p, Runnable r) {
    SkillRuntimeGuards.withPlayer(this, p, r);
  }

  protected void shouldReturnForPlayer(Player p, Cancellable c, Runnable r) {
    SkillRuntimeGuards.withPlayer(this, p, c, r);
  }

  protected boolean shouldReturnForWorld(World world, Skill<?> skill) {
    return SkillRuntimeGuards.shouldSkipWorld(skill, world);
  }

  protected boolean isWorldBlacklisted(Player p) {
    return SkillRuntimeGuards.isWorldBlacklisted(p);
  }

  protected boolean isInCreativeOrSpectator(Player p) {
    return SkillRuntimeGuards.isInCreativeOrSpectator(p);
  }

  protected FxEmitter fx(Location location, FxPriority priority) {
    return Fx.now(this, location, priority);
  }

  protected FxEmitter fx(Entity entity, FxPriority priority) {
    return Fx.now(this, entity, priority);
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

  protected <V> Map<UUID, V> playerState() {
    return PlayerStateRegistry.newPlayerMap();
  }

  protected Cooldowns cooldowns() {
    return PlayerStateRegistry.newCooldowns();
  }

  protected void addStat(Player p, String stat, double amount) {
    getPlayer(p).getData().addStat(stat, amount);
  }

  public void setColor(C color) {
    C resolved = color == null ? C.WHITE : color;
    this.color = resolved;
    this.colorPrefix = resolved.toString();
  }

  @Override
  public String getEmojiName() {
    return AdaptLanguage.text(presentation.icon());
  }

  @Override
  public String getDescription() {
    return AdaptLanguage.text(presentation.description());
  }

  @Override
  public String getLocalizedName() {
    return localizedName();
  }

  @Override
  public String getDisplayName() {
    if (!this.isEnabled()) {
      return C.DARK_GRAY + localizedName();
    }

    return C.RESET + "" + C.BOLD + colorPrefixValue() + getEmojiName() + " " + localizedName();
  }

  @Override
  public String getShortName() {
    if (!this.isEnabled()) {
      return C.DARK_GRAY + localizedName();
    }

    return C.RESET + "" + C.BOLD + colorPrefixValue() + getEmojiName();
  }

  @Override
  public String getDisplayName(int level) {
    if (!this.isEnabled()) {
      return C.DARK_GRAY + localizedName();
    }

    if (level > 0) {
      return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }

    return getDisplayName();
  }

  @Override
  public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {
    advancements.addAll(cachedAdvancements);
  }

  public AdaptAdvancement buildAdvancements() {
    KList<AdaptAdvancement> a = new KList<>();
    onRegisterAdvancements(a);

    for (Adaptation<?> i : getAdaptations()) {
      a.add(i.buildAdvancements());
    }

    return AdaptAdvancement.builder()
        .background(getAdvancementBackground())
        .key("skill_" + getName())
        .title(localizedName())
        .description(getDescription())
        .icon(getIcon())
        .model(getModel())
        .children(a)
        .visibility(AdvancementVisibility.HIDDEN)
        .build();
  }

  @Override
  public void registerStatTracker(AdaptStatTracker tracker) {
    getStatTrackers().add(tracker);
  }

  protected void registerMilestone(String advancementKey, String stat, double goal, double reward) {
    registerStatTracker(AdaptStatTracker.builder()
        .advancement(advancementKey)
        .stat(stat)
        .goal(goal)
        .reward(reward)
        .build());
  }

  protected void registerMilestone(String advancementKey, String stat, double goal, DoubleSupplier rewardSupplier) {
    registerStatTracker(AdaptStatTracker.builder()
        .advancement(advancementKey)
        .stat(stat)
        .goal(goal)
        .rewardSupplier(rewardSupplier)
        .build());
  }

  @Override
  public KList<AdaptStatTracker> getStatTrackers() {
    return statTrackers;
  }

  @Override
  public void registerAdaptation(Adaptation<?> a) {
    a.setSkill(this);
    adaptations.add(a);
  }

  @Override
  public void unregister() {
    if (!unregistered.compareAndSet(false, true)) {
      return;
    }
    super.unregister();
    adaptations.forEach(Adaptation::unregister);
  }

  private String colorPrefixValue() {
    if (colorPrefix == null || colorPrefix.isBlank()) {
      return color == null ? C.WHITE.toString() : color.toString();
    }
    return colorPrefix;
  }

  private String localizedName() {
    String localized = AdaptLanguage.text(presentation.name());
    return localized == null || localized.isBlank() ? Form.capitalize(getName()) : localized;
  }

  private String resolveColorPrefix(String rawInput) {
    String raw = rawInput == null ? "" : rawInput.trim();
    if (raw.isBlank()) {
      return null;
    }

    C named = parseNamedColor(raw);
    if (named != null) {
      return named.toString();
    }

    String normalizedHex = normalizeHex(raw);
    if (normalizedHex != null) {
      return C.translateAlternateColorCodes('&', "&#" + normalizedHex);
    }

    if (raw.indexOf(C.COLOR_CHAR) >= 0) {
      return raw;
    }

    String legacy = C.translateAlternateColorCodes('&', raw);
    if (legacy != null && legacy.indexOf(C.COLOR_CHAR) >= 0) {
      return legacy;
    }

    String mini = AdventureCompat.toLegacySection(raw);
    if (mini != null && mini.indexOf(C.COLOR_CHAR) >= 0) {
      return mini;
    }

    return null;
  }

  private C parseNamedColor(String raw) {
    String normalized = raw.trim().toUpperCase(Locale.ROOT)
        .replace('-', '_')
        .replace(' ', '_');
    try {
      C candidate = C.valueOf(normalized);
      if (candidate.isColor()) {
        return candidate;
      }
    } catch (IllegalArgumentException ignored) {
    }

    if (raw.length() == 1) {
      char code = Character.toLowerCase(raw.charAt(0));
      if (isLegacyColorChar(code)) {
        return C.getByChar(code);
      }
    }

    return null;
  }

  private String normalizeHex(String raw) {
    String value = raw;
    if (value.startsWith("#")) {
      value = value.substring(1);
    } else if (value.startsWith("0x") || value.startsWith("0X")) {
      value = value.substring(2);
    }

    if (value.length() != 6) {
      return null;
    }

    for (int i = 0; i < value.length(); i++) {
      if (!isHexChar(value.charAt(i))) {
        return null;
      }
    }

    return value;
  }

  private boolean isHexChar(char c) {
    return (c >= '0' && c <= '9')
        || (c >= 'a' && c <= 'f')
        || (c >= 'A' && c <= 'F');
  }

  private boolean isLegacyColorChar(char code) {
    return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
  }

  private C resolveLegacyColor(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      return color;
    }

    for (int i = 0; i < prefix.length() - 1; i++) {
      if (prefix.charAt(i) != C.COLOR_CHAR) {
        continue;
      }

      char code = Character.toLowerCase(prefix.charAt(i + 1));
      if (isLegacyColorChar(code)) {
        return C.getByChar(code);
      }

      if (code == 'x') {
        String hex = parseSectionHex(prefix, i);
        if (hex == null) {
          continue;
        }

        C nearest = nearestLegacyColor(hex);
        if (nearest != null) {
          return nearest;
        }
      }
    }

    return color;
  }

  private String parseSectionHex(String prefix, int start) {
    if (start + 13 >= prefix.length()) {
      return null;
    }

    StringBuilder hex = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      int colorTokenIndex = start + 2 + (i * 2);
      int hexIndex = colorTokenIndex + 1;
      if (colorTokenIndex >= prefix.length() || hexIndex >= prefix.length()) {
        return null;
      }
      if (prefix.charAt(colorTokenIndex) != C.COLOR_CHAR) {
        return null;
      }
      char hexChar = prefix.charAt(hexIndex);
      if (!isHexChar(hexChar)) {
        return null;
      }
      hex.append(hexChar);
    }
    return hex.toString();
  }

  private C nearestLegacyColor(String hex) {
    try {
      int rgb = Integer.parseInt(hex, 16);
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;

      C best = null;
      long bestDistance = Long.MAX_VALUE;
      for (C candidate : C.values()) {
        if (!candidate.isColor()) {
          continue;
        }

        String candidateHex = candidate.hex();
        if (candidateHex == null || candidateHex.length() != 7) {
          continue;
        }

        int candidateRgb = Integer.parseInt(candidateHex.substring(1), 16);
        int cr = (candidateRgb >> 16) & 0xFF;
        int cg = (candidateRgb >> 8) & 0xFF;
        int cb = candidateRgb & 0xFF;

        long distance = ((long) r - cr) * ((long) r - cr)
            + ((long) g - cg) * ((long) g - cg)
            + ((long) b - cb) * ((long) b - cb);
        if (distance < bestDistance) {
          bestDistance = distance;
          best = candidate;
        }
      }

      return best == null ? color : best;
    } catch (Throwable ignored) {
      return color;
    }
  }


  @Override
  public final boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }
}
