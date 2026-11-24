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
import art.arcane.adapt.api.Component;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.api.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.WorldPolicyLatencyTelemetry;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.tick.Ticked;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Public API for one adaptation under a skill line.
 */
public interface Adaptation<T> extends Ticked, Component {

  default List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED);
  }

  default <E extends Enum<E>> E preference(PlayerRef player, PlayerPreference<E> preference) {
    AdaptPlayer adaptPlayer = getPlayer(player);
    return PlayerPreferences.resolve(this, adaptPlayer == null ? null : adaptPlayer.getData(),
        getLevel(player), preference);
  }

  default boolean preferenceEnabled(PlayerRef player, PlayerPreference<CommonPreferences.Toggle> preference) {
    return preference(player, preference) == CommonPreferences.Toggle.ON;
  }

  default boolean isPlayerEnabled(PlayerRef player) {
    AdaptPlayer adaptPlayer = getPlayer(player);
    return adaptPlayer != null && adaptPlayer.isRuntimeReady()
        && PlayerPreferences.isEnabled(this, adaptPlayer.getData(), getLevel(adaptPlayer));
  }

  default void onPlayerPreferencesChanged(AdaptPlayer player) {
  }

  default boolean isPlayerPreferenceVisible(AdaptPlayer player, PlayerPreference<?> preference) {
    return true;
  }

  default List<PreferenceToggleGroup> getPlayerPreferenceGroups() {
    return List.of();
  }

  default boolean isPlayerPreferenceBlocked(PlayerPreference<?> preference) {
    return false;
  }

  /**
   * @return maximum unlockable level for this adaptation.
   */
  int getMaxLevel();

  default List<AdaptationRequirement> getRequirements() {
    return List.of();
  }

  /** What this adaptation needs from the Minecraft version; without it the adaptation is left out of its skill. */
  default List<PlatformNeed> getPlatformNeeds() {
    return List.of();
  }

  /**
   * Grants adaptation-attributed xp to a player.
   */
  default void xp(PlayerRef p, double amount) {
    xp(p, amount, null);
  }

  /**
   * Grants adaptation-attributed xp to a player with an optional reward key
   * suffix.
   */
  default void xp(PlayerRef p, double amount, String rewardKey) {
    if (!isPlayerEnabled(p) || !Double.isFinite(amount) || amount <= 0D) {
      return;
    }
    double baseline = AdaptationRuntimeGuards.claimUsageBaselineXp(this, p, getLevel(p));
    getSkill().xp(p, amount + baseline, adaptationRewardKey(rewardKey));
  }

  /**
   * Grants adaptation-attributed xp and renders it at a world location.
   */
  default void xp(PlayerRef p, Loc l, double amount) {
    xp(p, l, amount, null);
  }

  /**
   * Grants adaptation-attributed xp at a location with an optional reward key
   * suffix.
   */
  default void xp(PlayerRef p, Loc l, double amount, String rewardKey) {
    if (!isPlayerEnabled(p) || !Double.isFinite(amount) || amount <= 0D) {
      return;
    }
    double baseline = AdaptationRuntimeGuards.claimUsageBaselineXp(this, p, getLevel(p));
    getSkill().xp(p, l, amount + baseline, adaptationRewardKey(rewardKey));
  }

  /**
   * Grants silent adaptation-attributed xp with an optional reward key suffix.
   */
  default void xpSilent(PlayerRef p, double amount, String rewardKey) {
    if (!isPlayerEnabled(p) || !Double.isFinite(amount) || amount <= 0D) {
      return;
    }
    double baseline = AdaptationRuntimeGuards.claimUsageBaselineXp(this, p, getLevel(p));
    getSkill().xpSilent(p, amount + baseline, adaptationRewardKey(rewardKey));
  }

  /**
   * Grants silent adaptation-attributed xp.
   */
  default void xpSilent(PlayerRef p, double amount) {
    xpSilent(p, amount, null);
  }

  /**
   * Ensures this logic runs on the player's owned thread/region (Folia-safe).
   */
  default void withPlayerThread(PlayerRef p, Runnable runnable) {
    AdaptationRuntimeGuards.withPlayerThread(this, p, runnable);
  }

  /**
   * Thread/region guard with optional cancellable event gating.
   */
  default void withPlayerThread(PlayerRef p, Cancellable cancellable, Runnable runnable) {
    AdaptationRuntimeGuards.withPlayerThread(this, p, cancellable, runnable);
  }

  /**
   * Runs only when player-thread-safe and the player owns this adaptation.
   */
  default void withAdaptedPlayer(PlayerRef p, Runnable runnable) {
    AdaptationRuntimeGuards.withAdaptedPlayer(this, p, runnable);
  }

  /**
   * Runs only when event is not cancelled, player is thread-safe, and
   * adaptation is owned.
   */
  default void withAdaptedPlayer(PlayerRef p, Cancellable cancellable, Runnable runnable) {
    AdaptationRuntimeGuards.withAdaptedPlayer(this, p, cancellable, runnable);
  }

  /**
   * Resolves active runtime level and invokes consumer only when level > 0.
   */
  default void withActiveLevel(PlayerRef p, IntConsumer consumer) {
    AdaptationRuntimeGuards.withActiveLevel(this, p, consumer);
  }

  /**
   * Active-level helper with cancellable event gating.
   */
  default void withActiveLevel(PlayerRef p, Cancellable cancellable, IntConsumer consumer) {
    AdaptationRuntimeGuards.withActiveLevel(this, p, cancellable, consumer);
  }

  /**
   * Builds an adaptation-scoped reward key (`adaptation:<name>:<suffix>`).
   */
  default String adaptationRewardKey(String rewardKey) {
    return AdaptationRuntimeGuards.adaptationRewardKey(this, rewardKey);
  }

  /**
   * Adaptation-aware particle toggle check (global + skill + adaptation config
   * aware).
   */
  @Override
  default boolean areParticlesEnabled() {
    return AdaptationGuiSupport.areParticlesEnabled(this, Component.super.areParticlesEnabled());
  }

  /**
   * Adaptation-aware sound toggle check (global + skill + adaptation config
   * aware).
   */
  @Override
  default boolean areSoundsEnabled() {
    return AdaptationGuiSupport.areSoundsEnabled(this, Component.super.areSoundsEnabled());
  }

  /**
   * Grants small anti-abuse baseline xp for successful adaptation use.
   */
  default void awardUsageBaselineXp(PlayerRef p, int level) {
    AdaptationRuntimeGuards.awardUsageBaselineXp(this, p, level);
  }

  /**
   * Reads adaptation-scoped per-player storage, falling back to default value.
   */
  default <F> F getStorage(PlayerRef p, String key, F defaultValue) {
    return AdaptationRuntimeGuards.getStorage(this, p, key, defaultValue);
  }

  /**
   * Reads adaptation-scoped per-player storage.
   */
  default <F> F getStorage(PlayerRef p, String key) {
    return getStorage(p, key, null);
  }

  /**
   * Writes adaptation-scoped per-player storage.
   */
  default boolean setStorage(PlayerRef p, String key, Object value) {
    return AdaptationRuntimeGuards.setStorage(this, p, key, value);
  }

  /**
   * Fires use checks/events for this adaptation against an AdaptPlayer
   * context.
   */
  default boolean canUse(AdaptPlayer player) {
    return AdaptationRuntimeGuards.canUse(this, player);
  }

  /**
   * Fires use checks/events for this adaptation against a platform player.
   */
  default boolean canUse(PlayerRef player) {
    return AdaptationRuntimeGuards.canUse(this, player);
  }

  /**
   * Returns true when this player has use permission for this adaptation via
   * permission.
   */
  default boolean hasUsePermission(PlayerRef p, Adaptation<?> a) {
    return AdaptationRuntimeGuards.hasUsePermission(this, p, a);
  }

  /**
   * Typed storage convenience helper for strings.
   */
  default String getStorageString(PlayerRef p, String key, String defaultValue) {
    return getStorage(p, key, defaultValue);
  }

  /**
   * Typed storage convenience helper for strings.
   */
  default String getStorageString(PlayerRef p, String key) {
    return getStorage(p, key);
  }

  /**
   * Typed storage convenience helper for integers.
   */
  default Integer getStorageInt(PlayerRef p, String key, Integer defaultValue) {
    Object value = getStorage(p, key, (Object) defaultValue);
    if (value == null) {
      return defaultValue;
    }

    if (value instanceof Number number) {
      return number.intValue();
    }

    if (value instanceof String stringValue) {
      try {
        return Integer.parseInt(stringValue.trim());
      } catch (NumberFormatException ignored) {
        return defaultValue;
      }
    }

    return defaultValue;
  }

  /**
   * Typed storage convenience helper for integers.
   */
  default Integer getStorageInt(PlayerRef p, String key) {
    return getStorageInt(p, key, null);
  }

  /**
   * Typed storage convenience helper for doubles.
   */
  default Double getStorageDouble(PlayerRef p, String key, Double defaultValue) {
    Object value = getStorage(p, key, (Object) defaultValue);
    if (value == null) {
      return defaultValue;
    }

    if (value instanceof Number number) {
      return number.doubleValue();
    }

    if (value instanceof String stringValue) {
      try {
        return Double.parseDouble(stringValue.trim());
      } catch (NumberFormatException ignored) {
        return defaultValue;
      }
    }

    return defaultValue;
  }

  /**
   * Typed storage convenience helper for doubles.
   */
  default Double getStorageDouble(PlayerRef p, String key) {
    return getStorageDouble(p, key, null);
  }

  /**
   * Typed storage convenience helper for booleans.
   */
  default Boolean getStorageBoolean(PlayerRef p, String key, Boolean defaultValue) {
    return getStorage(p, key, defaultValue);
  }

  /**
   * Typed storage convenience helper for booleans.
   */
  default Boolean getStorageBoolean(PlayerRef p, String key) {
    return getStorage(p, key);
  }

  /**
   * Typed storage convenience helper for longs.
   */
  default Long getStorageLong(PlayerRef p, String key, Long defaultValue) {
    Object value = getStorage(p, key, (Object) defaultValue);
    if (value == null) {
      return defaultValue;
    }

    if (value instanceof Number number) {
      return number.longValue();
    }

    if (value instanceof String stringValue) {
      try {
        return Long.parseLong(stringValue.trim());
      } catch (NumberFormatException ignored) {
        return defaultValue;
      }
    }

    return defaultValue;
  }

  /**
   * Typed storage convenience helper for longs.
   */
  default Long getStorageLong(PlayerRef p, String key) {
    return getStorageLong(p, key, null);
  }

  /**
   * Returns the concrete config class used by this adaptation.
   */
  Class<T> getConfigurationClass();

  /**
   * Registers the config class used for load/reload of this adaptation.
   */
  void registerConfiguration(Class<T> type);

  /**
   * @return true when this adaptation is runtime-enabled.
   */
  boolean isEnabled();

  /**
   * @return true when this adaptation cannot be unlearned.
   */
  boolean isPermanent();

  /**
   * Returns the live config instance for this adaptation.
   */
  T getConfig();

  /**
   * Builds the advancement tree node for this adaptation.
   */
  AdaptAdvancement buildAdvancements();

  /**
   * Adds per-level stat lines to GUI elements.
   */
  void addStats(int level, StatLines v);

  /**
   * @return base upgrade cost.
   */
  int getBaseCost();

  /**
   * @return localized adaptation description.
   */
  String getDescription();

  /**
   * @return base icon for this adaptation.
   */
  Id getIcon();

  /**
   * @return owning skill of this adaptation.
   */
  Skill<?> getSkill();

  /**
   * Assigns the owning skill for this adaptation.
   */
  void setSkill(Skill<?> skill);

  /**
   * @return internal adaptation key.
   */
  String getName();

  /**
   * @return initial unlock/upgrade cost component.
   */
  int getInitialCost();

  /**
   * @return per-level cost multiplier.
   */
  double getCostFactor();

  /**
   * @return recipes registered by this adaptation.
   */
  List<RecipeSpec> getRecipes();

  /**
   * @return custom brewing recipes registered by this adaptation.
   */
  List<BrewingRecipe> getBrewingRecipes();

  /**
   * Called while building advancement trees to append custom nodes.
   */
  void onRegisterAdvancements(List<AdaptAdvancement> advancements);

  default void onLevelChanged(PlayerRef player, int level) {
  }

  /**
   * Returns the names of the active protectors after applying this adaptation's
   * override config.
   */
  default Set<String> getProtectorNames() {
    return AdaptationRuntimeGuards.getProtectorNames(this);
  }

  /**
   * World-policy check for breaking a block.
   */
  default boolean canBlockBreak(PlayerRef player, Loc blockLocation) {
    return evaluateWorldPolicy(ProtectionCheck.BLOCK_BREAK, player, blockLocation, null);
  }

  /**
   * World-policy check for placing a block.
   */
  default boolean canBlockPlace(PlayerRef player, Loc blockLocation) {
    return evaluateWorldPolicy(ProtectionCheck.BLOCK_PLACE, player, blockLocation, null);
  }

  /**
   * World-policy check for PVP damage.
   */
  default boolean canPVP(PlayerRef player, Loc victimLocation) {
    return evaluateWorldPolicy(ProtectionCheck.PVP, player, victimLocation, null);
  }

  /**
   * World-policy check for PVE damage.
   */
  default boolean canPVE(PlayerRef player, Loc victimLocation) {
    return evaluateWorldPolicy(ProtectionCheck.PVE, player, victimLocation, null);
  }

  default boolean canPVE(PlayerRef player, EntityRef victim) {
    return evaluateWorldPolicy(ProtectionCheck.PVE, player, victim == null ? null : victim.location(), victim);
  }

  /**
   * World-policy check for generic interaction.
   */
  default boolean canInteract(PlayerRef player, Loc targetLocation) {
    return evaluateWorldPolicy(ProtectionCheck.INTERACT, player, targetLocation, null);
  }

  /**
   * Returns true when attacker can damage target under current world policies.
   */
  default boolean canDamageTarget(PlayerRef attacker, EntityRef target) {
    return AdaptationRuntimeGuards.canDamageTarget(this, attacker, target);
  }

  default boolean isProtectedFriendly(PlayerRef actor, EntityRef target) {
    return AdaptationRuntimeGuards.isProtectedFriendly(actor, target);
  }

  default boolean isProtectedFriendlyOwned(UUID actorId, EntityRef target) {
    return AdaptationRuntimeGuards.isProtectedFriendlyOwned(actorId, target);
  }

  /**
   * Returns active level only when player is in survival.
   */
  default int getActiveSurvivalLevel(PlayerRef player) {
    return AdaptationRuntimeGuards.getActiveSurvivalLevel(this, player);
  }

  /**
   * Returns active level only when the player meets the supplied requirement.
   */
  default int getActiveLevel(PlayerRef player, Predicate<PlayerRef> requirement) {
    return AdaptationRuntimeGuards.getActiveLevel(this, player, requirement);
  }

  /**
   * Returns active-survival level only when the player meets the supplied
   * requirement.
   */
  default int getActiveSurvivalLevel(PlayerRef player, Predicate<PlayerRef> requirement) {
    return AdaptationRuntimeGuards.getActiveSurvivalLevel(this, player, requirement);
  }

  /**
   * Returns active level gated by interaction permission at a location.
   */
  default int getActiveInteractLevel(PlayerRef player, Loc location) {
    return AdaptationRuntimeGuards.getActiveInteractLevel(this, player, location);
  }

  /**
   * Returns active level gated by block-break permission at a location.
   */
  default int getActiveBlockBreakLevel(PlayerRef player, Loc location) {
    return AdaptationRuntimeGuards.getActiveBlockBreakLevel(this, player, location);
  }

  /**
   * Returns active level gated by block-place permission at a location.
   */
  default int getActiveBlockPlaceLevel(PlayerRef player, Loc location) {
    return AdaptationRuntimeGuards.getActiveBlockPlaceLevel(this, player, location);
  }

  /**
   * Returns active level gated by PVP/PVE policy for a specific target.
   */
  default int getActiveDamageLevel(PlayerRef attacker, EntityRef target) {
    return AdaptationRuntimeGuards.getActiveDamageLevel(this, attacker, target);
  }

  /**
   * Resolves a context only when adaptation is active and interact is allowed.
   */
  default BlockActionContext resolveInteractContext(PlayerRef player, Loc location) {
    return AdaptationRuntimeGuards.resolveInteractContext(this, player, location);
  }

  /**
   * Resolves interact context and applies an additional player requirement.
   */
  default BlockActionContext resolveInteractContext(PlayerRef player, Loc location, Predicate<PlayerRef> requirement) {
    return AdaptationRuntimeGuards.resolveInteractContext(this, player, location, requirement);
  }

  /**
   * Resolves interact context with optional requirement and survival-only
   * gating.
   */
  default BlockActionContext resolveInteractContext(PlayerRef player, Loc location, Predicate<PlayerRef> requirement, boolean survivalOnly) {
    return AdaptationRuntimeGuards.resolveInteractContext(this, player, location, requirement, survivalOnly);
  }

  /**
   * Resolves a context only when adaptation is active and block break is
   * allowed.
   */
  default BlockActionContext resolveBlockBreakContext(PlayerRef player, Loc location) {
    return AdaptationRuntimeGuards.resolveBlockBreakContext(this, player, location);
  }

  /**
   * Resolves block-break context and applies an additional player requirement.
   */
  default BlockActionContext resolveBlockBreakContext(PlayerRef player, Loc location, Predicate<PlayerRef> requirement) {
    return AdaptationRuntimeGuards.resolveBlockBreakContext(this, player, location, requirement);
  }

  /**
   * Resolves block-break context with optional requirement and survival-only
   * gating.
   */
  default BlockActionContext resolveBlockBreakContext(PlayerRef player, Loc location, Predicate<PlayerRef> requirement, boolean survivalOnly) {
    return AdaptationRuntimeGuards.resolveBlockBreakContext(this, player, location, requirement, survivalOnly);
  }

  /**
   * Resolves a context only when adaptation is active and block place is
   * allowed.
   */
  default BlockActionContext resolveBlockPlaceContext(PlayerRef player, Loc location) {
    return AdaptationRuntimeGuards.resolveBlockPlaceContext(this, player, location);
  }

  /**
   * Resolves block-place context and applies an additional player requirement.
   */
  default BlockActionContext resolveBlockPlaceContext(PlayerRef player, Loc location, Predicate<PlayerRef> requirement) {
    return AdaptationRuntimeGuards.resolveBlockPlaceContext(this, player, location, requirement);
  }

  /**
   * Resolves block-place context with optional requirement and survival-only
   * gating.
   */
  default BlockActionContext resolveBlockPlaceContext(PlayerRef player, Loc location, Predicate<PlayerRef> requirement, boolean survivalOnly) {
    return AdaptationRuntimeGuards.resolveBlockPlaceContext(this, player, location, requirement, survivalOnly);
  }

  /**
   * Resolves a context requiring both interact and block-break permission.
   */
  default BlockActionContext resolveInteractBreakContext(PlayerRef player, Loc location) {
    return AdaptationRuntimeGuards.resolveInteractBreakContext(this, player, location);
  }

  /**
   * Resolves interact+break context and applies an additional player
   * requirement.
   */
  default BlockActionContext resolveInteractBreakContext(PlayerRef player, Loc location, Predicate<PlayerRef> requirement) {
    return AdaptationRuntimeGuards.resolveInteractBreakContext(this, player, location, requirement);
  }

  /**
   * Resolves interact+break context with optional requirement and survival-only
   * gating.
   */
  default BlockActionContext resolveInteractBreakContext(PlayerRef player, Loc location, Predicate<PlayerRef> requirement, boolean survivalOnly) {
    return AdaptationRuntimeGuards.resolveInteractBreakContext(this, player, location, requirement, survivalOnly);
  }

  /**
   * Returns a validated main-hand item when adaptation is active and
   * requirement passes.
   */
  default ItemRef readyMainHand(PlayerRef player, Predicate<ItemRef> requirement) {
    return AdaptationRuntimeGuards.readyMainHand(this, player, requirement);
  }

  /**
   * Returns a validated active main-hand item without extra requirement
   * checks.
   */
  default ItemRef readyMainHand(PlayerRef player) {
    return readyMainHand(player, null);
  }

  /**
   * Resolves melee combat context from an entity-damage event.
   */
  default MeleeContext resolveMeleeContext(EntityDamageByEntityEvent event, Predicate<ItemRef> mainHandRequirement) {
    return AdaptationRuntimeGuards.resolveMeleeContext(this, event, mainHandRequirement);
  }

  /**
   * Resolves melee combat context from an entity-damage event.
   */
  default MeleeContext resolveMeleeContext(EntityDamageByEntityEvent event) {
    return resolveMeleeContext(event, null);
  }

  /**
   * Resolves generic player attack context from an entity-damage event.
   */
  default AttackContext resolveAttackContext(EntityDamageByEntityEvent event, Predicate<ItemRef> mainHandRequirement) {
    return AdaptationRuntimeGuards.resolveAttackContext(this, event, mainHandRequirement);
  }

  /**
   * Resolves generic player attack context from an entity-damage event.
   */
  default AttackContext resolveAttackContext(EntityDamageByEntityEvent event) {
    return resolveAttackContext(event, null);
  }

  /**
   * Resolves projectile combat context from an entity-damage event.
   */
  default ProjectileContext resolveProjectileContext(EntityDamageByEntityEvent event, Predicate<ProjectileRef> projectileRequirement) {
    return AdaptationRuntimeGuards.resolveProjectileContext(this, event, projectileRequirement);
  }

  /**
   * Resolves projectile combat context from an entity-damage event.
   */
  default ProjectileContext resolveProjectileContext(EntityDamageByEntityEvent event) {
    return resolveProjectileContext(event, null);
  }

  /**
   * World-policy check for chest access.
   */
  default boolean canAccessChest(PlayerRef player, Loc chestLocation) {
    return evaluateWorldPolicy(ProtectionCheck.ACCESS_CHEST, player, chestLocation, null);
  }

  /**
   * World-policy check for generic region access at the player's current
   * location.
   */
  default boolean checkRegion(PlayerRef player) {
    return checkRegion(player, player.location());
  }

  default boolean checkRegion(PlayerRef player, Loc location) {
    return evaluateWorldPolicy(ProtectionCheck.REGION, player, location, null);
  }

  private boolean evaluateWorldPolicy(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim) {
    long start = System.nanoTime();
    try {
      Set<String> protectors = getProtectorNames();
      return protectors.isEmpty()
          || AdaptationRuntimeGuards.protectorPort().allows(protectors, check, player, location, victim, this);
    } finally {
      WorldPolicyLatencyTelemetry.recordNanos(System.nanoTime() - start);
    }
  }

  /**
   * Returns true when this adaptation currently conflicts with another active
   * adaptation.
   */
  default boolean hasUsageConflict(PlayerRef p) {
    return AdaptationRuntimeGuards.hasUsageConflict(this, p);
  }

  /**
   * Runtime-ready level check (ownership + enabled + world/protection/conflict
   * checks).
   */
  default int getActiveLevel(PlayerRef p) {
    return AdaptationRuntimeGuards.getActiveLevel(this, p);
  }

  default int getActiveDeathLevel(PlayerRef p) {
    return AdaptationRuntimeGuards.getActiveDeathLevel(this, p);
  }

  default int getActiveSiblingLevel(PlayerRef p, String adaptationName) {
    Skill<?> skill = getSkill();
    if (p == null || adaptationName == null || skill == null) {
      return 0;
    }
    for (Adaptation<?> sibling : skill.getAdaptations()) {
      if (adaptationName.equals(sibling.getName())) {
        return sibling.getActiveLevel(p);
      }
    }
    return 0;
  }

  default int getActiveSiblingBlockBreakLevel(PlayerRef p, String adaptationName, Loc location) {
    Skill<?> skill = getSkill();
    if (p == null || adaptationName == null || location == null || skill == null) {
      return 0;
    }
    for (Adaptation<?> sibling : skill.getAdaptations()) {
      if (adaptationName.equals(sibling.getName())) {
        return sibling.getActiveBlockBreakLevel(p, location);
      }
    }
    return 0;
  }

  /**
   * Ownership check only (learned level > 0), without runtime gating.
   */
  default boolean hasAdaptation(PlayerRef p) {
    return getLevel(p) > 0;
  }

  /**
   * Runtime-usable state (ownership + active world/permission/conflict
   * checks).
   */
  default boolean hasActiveAdaptation(PlayerRef p) {
    return getActiveLevel(p) > 0;
  }

  /**
   * Learned level capped by the current configured maximum, while ignoring
   * runtime gating such as world, protection, and conflict checks.
   */
  default int getLevel(PlayerRef p) {
    return AdaptationRuntimeGuards.getLevel(this, p);
  }

  default int getLevel(AdaptPlayer adaptPlayer) {
    return AdaptationRuntimeGuards.getLevel(this, adaptPlayer);
  }

  default boolean hasLearnedAdaptation(AdaptPlayer adaptPlayer) {
    return getLevel(adaptPlayer) > 0;
  }

  default List<AdaptPlayer> learnedCandidates(long now) {
    return AdaptationRuntimeGuards.learnedCandidates(this, now);
  }

  /**
   * Learned level normalized to 0..1.
   */
  default double getLevelPercent(PlayerRef p) {
    if (!this.isEnabled()) {
      return 0;
    }
    if (!this.getSkill().isEnabled()) {
      return 0;
    }
    if (!AdaptationRuntimeGuards.isServerPlayer(p)) {
      return 0.0;
    }
    int maxLevel = getMaxLevel();
    if (maxLevel <= 0) {
      return 0D;
    }
    return Math.min(Math.max(0, M.lerpInverse(0, maxLevel, getLevel(p))), 1);
  }

  /**
   * Level normalized to 0..1 using an explicit level value.
   */
  default double getLevelPercent(int p) {
    int maxLevel = getMaxLevel();
    if (maxLevel <= 0) {
      return 0D;
    }
    return Math.min(Math.max(0, M.lerpInverse(0, maxLevel, p)), 1);
  }

  /**
   * Cost for purchasing exactly this level step.
   */
  default int getCostFor(int level) {
    double scaledCost = Math.max(1D, getBaseCost() + (getBaseCost() * (level * getCostFactor())));
    double totalCost = scaledCost + (level == 1 ? getInitialCost() : 0);
    if (!Double.isFinite(totalCost) || totalCost >= Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return Math.max(1, (int) totalCost);
  }

  /**
   * Power change for a level transition: a held adaptation uses 1 power at any level, so learning it from level 0
   * costs 1, unlearning it to level 0 frees 1, and every other change costs nothing.
   */
  default int getPowerCostFor(int level, int myLevel) {
    boolean holds = myLevel > 0;
    boolean willHold = level > 0;
    if (holds == willHold) {
      return 0;
    }
    return willHold ? 1 : -1;
  }

  /**
   * Cumulative cost to move from current level to target level.
   */
  default int getCostFor(int level, int myLevel) {
    if (myLevel >= level) {
      return 0;
    }


    long c = 0L;

    for (int i = myLevel + 1; i <= level; i++) {
      c += getCostFor(i);
      if (c >= Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
    }

    return (int) c;
  }

  /**
   * Cumulative refund amount when reducing from current level to target level.
   */
  default int getRefundCostFor(int level, int myLevel) {
    if (myLevel <= level) {
      return 0;
    }

    long c = 0L;

    for (int i = level + 1; i <= myLevel; i++) {
      c += getCostFor(i);
      if (c >= Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
    }

    return (int) c;
  }

  /**
   * UI display name for this adaptation.
   */
  default String getDisplayName() {
    return AdaptationGuiSupport.getDisplayName(this);
  }

  /**
   * UI display name with level suffix.
   */
  default String getDisplayName(int level) {
    return AdaptationGuiSupport.getDisplayName(this, level);
  }

  /**
   * UI display name with numeric level but no roman formatting.
   */
  default String getDisplayNameNoRoman(int level) {
    return AdaptationGuiSupport.getDisplayNameNoRoman(this, level);
  }

  /**
   * Returns targeted block face from player look direction.
   */
  default BlockFace getBlockFace(PlayerRef player, int maxrange) {
    return AdaptationGuiSupport.getBlockFace(player, maxrange);
  }

  /**
   * Returns generated custom model binding for this adaptation icon.
   */
  default CustomModel getModel() {
    return AdaptationGuiSupport.getModel(this);
  }

  /**
   * Returns generated custom model binding for a specific adaptation level.
   */
  default CustomModel getModel(int level) {
    return AdaptationGuiSupport.getModel(this, level);
  }

  /**
   * Opens adaptation GUI and optionally applies use permission checks.
   */
  default boolean openGui(PlayerRef player, boolean checkPermissions) {
    return AdaptationGuiSupport.openGui(this, player, checkPermissions);
  }

  /**
   * Opens page 0 of this adaptation GUI.
   */
  default void openGui(PlayerRef player) {
    AdaptationGuiSupport.openGui(this, player);
  }

  /**
   * Opens a specific page of this adaptation GUI.
   */
  default void openGui(PlayerRef player, int page) {
    AdaptationGuiSupport.openGui(this, player, page);
  }

  /**
   * Checks whether permanent learn confirmation is pending for this
   * player/level.
   */
  default boolean isPermanentLearnConfirmationPending(PlayerRef player, int level) {
    return AdaptationGuiSupport.isPermanentLearnConfirmationPending(player, this, level);
  }

  /**
   * Consumes pending permanent learn confirmation for this player/level.
   */
  default boolean consumePermanentLearnConfirmation(PlayerRef player, int level) {
    return AdaptationGuiSupport.consumePermanentLearnConfirmation(player, this, level);
  }

  /**
   * Unlearns levels from this adaptation.
   */
  default AdaptationLearningTransaction.Result unlearn(PlayerRef player, int lvl, boolean force) {
    return AdaptationGuiSupport.unlearn(this, player, lvl, force);
  }

  /**
   * Learns levels into this adaptation.
   */
  default AdaptationLearningTransaction.Result learn(PlayerRef player, int lvl, boolean force) {
    return AdaptationGuiSupport.learn(this, player, lvl, force);
  }

  /**
   * Returns true when the recipe with this key belongs to this adaptation.
   */
  default boolean isAdaptationRecipe(Id recipeKey) {
    if (recipeKey == null || !isEnabled() || !getSkill().isEnabled()) {
      return false;
    }
    for (RecipeSpec recipe : getRecipes()) {
      if (recipeKey.equals(recipe.key())) {
        return true;
      }
    }
    return false;
  }

  @Override
  default void damageHand(PlayerRef p, int damage) {
    payDurabilityCost(p, "durability", damage, () -> {
      Component.super.damageHand(p, damage);
      return true;
    });
  }

  @Override
  default void damageOffHand(PlayerRef p, int damage) {
    payDurabilityCost(p, "durability", damage, () -> {
      Component.super.damageOffHand(p, damage);
      return true;
    });
  }

  default boolean payItemCost(PlayerRef p, String costKey, ItemRef unit, int amount, AbilityDefaultCost defaultCost) {
    return AbilityApiBridge.charge(this, p, costKey, AbilityCostKind.ITEM, unit, amount, defaultCost);
  }

  default boolean payHungerCost(PlayerRef p, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return AbilityApiBridge.charge(this, p, costKey, AbilityCostKind.HUNGER, null, amount, defaultCost);
  }

  default boolean payHealthCost(PlayerRef p, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return AbilityApiBridge.charge(this, p, costKey, AbilityCostKind.HEALTH, null, amount, defaultCost);
  }

  default boolean payDurabilityCost(PlayerRef p, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return AbilityApiBridge.charge(this, p, costKey, AbilityCostKind.DURABILITY, null, amount, defaultCost);
  }

  default boolean payExperienceCost(PlayerRef p, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return AbilityApiBridge.charge(this, p, costKey, AbilityCostKind.VANILLA_EXPERIENCE, null, amount, defaultCost);
  }

  default AbilityCharge payItemCostDeferred(PlayerRef p, String costKey, ItemRef unit, int amount,
                                            AbilityDefaultCost defaultCost) {
    return AbilityApiBridge.chargeDeferred(this, p, costKey, AbilityCostKind.ITEM, unit, amount, defaultCost);
  }

  default AbilityCharge payExperienceCostDeferred(PlayerRef p, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return AbilityApiBridge.chargeDeferred(this, p, costKey, AbilityCostKind.VANILLA_EXPERIENCE, null, amount, defaultCost);
  }

  default boolean settleCost(UUID activationId) {
    return AbilityApiBridge.settle(activationId);
  }

  default boolean refundCost(UUID activationId, AbilityRefundReason reason) {
    return AbilityApiBridge.refund(activationId, reason);
  }

  /**
   * Generic attack context payload.
   */
  record AttackContext(PlayerRef attacker, EntityRef target, ItemRef mainHand,
                       int level) {
  }

  /**
   * Block-action context payload.
   */
  record BlockActionContext(PlayerRef player, Loc location, int level) {
  }

  /**
   * Melee-specific context payload.
   */
  record MeleeContext(PlayerRef attacker, LivingRef target, ItemRef mainHand,
                      int level) {
  }

  /**
   * Projectile-specific context payload.
   */
  record ProjectileContext(PlayerRef attacker, LivingRef target,
                           ProjectileRef projectile, int level) {
  }
}
