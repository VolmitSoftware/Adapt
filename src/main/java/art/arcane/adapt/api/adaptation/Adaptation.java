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

import art.arcane.adapt.api.Component;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.protection.WorldPolicyLatencyTelemetry;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.tick.Ticked;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Public API for one adaptation under a skill line.
 */
public interface Adaptation<T> extends Ticked, Component {

  /**
   * @return maximum unlockable level for this adaptation.
   */
  int getMaxLevel();

  /**
   * Grants adaptation-attributed xp to a player.
   */
  default void xp(Player p, double amount) {
    xp(p, amount, null);
  }

  /**
   * Grants adaptation-attributed xp to a player with an optional reward key
   * suffix.
   */
  default void xp(Player p, double amount, String rewardKey) {
    getSkill().xp(p, amount, adaptationRewardKey(rewardKey));
  }

  /**
   * Grants adaptation-attributed xp and renders it at a world location.
   */
  default void xp(Player p, Location l, double amount) {
    xp(p, l, amount, null);
  }

  /**
   * Grants adaptation-attributed xp at a location with an optional reward key
   * suffix.
   */
  default void xp(Player p, Location l, double amount, String rewardKey) {
    getSkill().xp(p, l, amount, adaptationRewardKey(rewardKey));
  }

  /**
   * Grants silent adaptation-attributed xp with an optional reward key suffix.
   */
  default void xpSilent(Player p, double amount, String rewardKey) {
    getSkill().xpSilent(p, amount, adaptationRewardKey(rewardKey));
  }

  /**
   * Grants silent adaptation-attributed xp.
   */
  default void xpSilent(Player p, double amount) {
    xpSilent(p, amount, null);
  }

  /**
   * Ensures this logic runs on the player's owned thread/region (Folia-safe).
   */
  default void withPlayerThread(Player p, Runnable runnable) {
    AdaptationRuntimeGuards.withPlayerThread(this, p, runnable);
  }

  /**
   * Thread/region guard with optional cancellable event gating.
   */
  default void withPlayerThread(Player p, Cancellable cancellable, Runnable runnable) {
    AdaptationRuntimeGuards.withPlayerThread(this, p, cancellable, runnable);
  }

  /**
   * Runs only when player-thread-safe and the player owns this adaptation.
   */
  default void withAdaptedPlayer(Player p, Runnable runnable) {
    AdaptationRuntimeGuards.withAdaptedPlayer(this, p, runnable);
  }

  /**
   * Runs only when event is not cancelled, player is thread-safe, and
   * adaptation is owned.
   */
  default void withAdaptedPlayer(Player p, Cancellable cancellable, Runnable runnable) {
    AdaptationRuntimeGuards.withAdaptedPlayer(this, p, cancellable, runnable);
  }

  /**
   * Resolves active runtime level and invokes consumer only when level > 0.
   */
  default void withActiveLevel(Player p, IntConsumer consumer) {
    AdaptationRuntimeGuards.withActiveLevel(this, p, consumer);
  }

  /**
   * Active-level helper with cancellable event gating.
   */
  default void withActiveLevel(Player p, Cancellable cancellable, IntConsumer consumer) {
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
  default void awardUsageBaselineXp(Player p, int level) {
    AdaptationRuntimeGuards.awardUsageBaselineXp(this, p, level);
  }

  /**
   * Reads adaptation-scoped per-player storage, falling back to default value.
   */
  default <F> F getStorage(Player p, String key, F defaultValue) {
    return AdaptationRuntimeGuards.getStorage(this, p, key, defaultValue);
  }

  /**
   * Reads adaptation-scoped per-player storage.
   */
  default <F> F getStorage(Player p, String key) {
    return getStorage(p, key, null);
  }

  /**
   * Writes adaptation-scoped per-player storage.
   */
  default boolean setStorage(Player p, String key, Object value) {
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
   * Fires use checks/events for this adaptation against a Bukkit player.
   */
  default boolean canUse(Player player) {
    return AdaptationRuntimeGuards.canUse(this, player);
  }

  /**
   * Returns true when this player is blacklisted from this adaptation via
   * permission.
   */
  default boolean hasBlacklistPermission(Player p, Adaptation a) {
    return AdaptationRuntimeGuards.hasBlacklistPermission(this, p, a);
  }

  /**
   * Typed storage convenience helper for strings.
   */
  default String getStorageString(Player p, String key, String defaultValue) {
    return getStorage(p, key, defaultValue);
  }

  /**
   * Typed storage convenience helper for strings.
   */
  default String getStorageString(Player p, String key) {
    return getStorage(p, key);
  }

  /**
   * Typed storage convenience helper for integers.
   */
  default Integer getStorageInt(Player p, String key, Integer defaultValue) {
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
  default Integer getStorageInt(Player p, String key) {
    return getStorageInt(p, key, null);
  }

  /**
   * Typed storage convenience helper for doubles.
   */
  default Double getStorageDouble(Player p, String key, Double defaultValue) {
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
  default Double getStorageDouble(Player p, String key) {
    return getStorageDouble(p, key, null);
  }

  /**
   * Typed storage convenience helper for booleans.
   */
  default Boolean getStorageBoolean(Player p, String key, Boolean defaultValue) {
    return getStorage(p, key, defaultValue);
  }

  /**
   * Typed storage convenience helper for booleans.
   */
  default Boolean getStorageBoolean(Player p, String key) {
    return getStorage(p, key);
  }

  /**
   * Typed storage convenience helper for longs.
   */
  default Long getStorageLong(Player p, String key, Long defaultValue) {
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
  default Long getStorageLong(Player p, String key) {
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
  void addStats(int level, Element v);

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
  Material getIcon();

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
  List<AdaptRecipe> getRecipes();

  /**
   * @return brewing recipes registered by this adaptation.
   */
  List<BrewingRecipe> getBrewingRecipes();

  /**
   * Called while building advancement trees to append custom nodes.
   */
  void onRegisterAdvancements(List<AdaptAdvancement> advancements);

  /**
   * Returns the active protector set after applying this adaptation's override
   * config.
   */
  default Set<Protector> getProtectors() {
    return AdaptationRuntimeGuards.getProtectors(this);
  }

  /**
   * World-policy check for breaking a block.
   */
  default boolean canBlockBreak(Player player, Location blockLocation) {
    return evaluateWorldPolicy(protector -> protector.canBlockBreak(player, blockLocation, this));
  }

  /**
   * World-policy check for placing a block.
   */
  default boolean canBlockPlace(Player player, Location blockLocation) {
    return evaluateWorldPolicy(protector -> protector.canBlockPlace(player, blockLocation, this));
  }

  /**
   * World-policy check for PVP damage.
   */
  default boolean canPVP(Player player, Location victimLocation) {
    return evaluateWorldPolicy(protector -> protector.canPVP(player, victimLocation, this));
  }

  /**
   * World-policy check for PVE damage.
   */
  default boolean canPVE(Player player, Location victimLocation) {
    return evaluateWorldPolicy(protector -> protector.canPVE(player, victimLocation, this));
  }

  /**
   * World-policy check for generic interaction.
   */
  default boolean canInteract(Player player, Location targetLocation) {
    return evaluateWorldPolicy(protector -> protector.canInteract(player, targetLocation, this));
  }

  /**
   * Returns true when attacker can damage target under current world policies.
   */
  default boolean canDamageTarget(Player attacker, Entity target) {
    return AdaptationRuntimeGuards.canDamageTarget(this, attacker, target);
  }

  /**
   * Returns active level only when player is in survival.
   */
  default int getActiveSurvivalLevel(Player player) {
    return AdaptationRuntimeGuards.getActiveSurvivalLevel(this, player);
  }

  /**
   * Returns active level only when the player meets the supplied requirement.
   */
  default int getActiveLevel(Player player, Predicate<Player> requirement) {
    return AdaptationRuntimeGuards.getActiveLevel(this, player, requirement);
  }

  /**
   * Returns active-survival level only when the player meets the supplied
   * requirement.
   */
  default int getActiveSurvivalLevel(Player player, Predicate<Player> requirement) {
    return AdaptationRuntimeGuards.getActiveSurvivalLevel(this, player, requirement);
  }

  /**
   * Returns active level gated by interaction permission at a location.
   */
  default int getActiveInteractLevel(Player player, Location location) {
    return AdaptationRuntimeGuards.getActiveInteractLevel(this, player, location);
  }

  /**
   * Returns active level gated by block-break permission at a location.
   */
  default int getActiveBlockBreakLevel(Player player, Location location) {
    return AdaptationRuntimeGuards.getActiveBlockBreakLevel(this, player, location);
  }

  /**
   * Returns active level gated by block-place permission at a location.
   */
  default int getActiveBlockPlaceLevel(Player player, Location location) {
    return AdaptationRuntimeGuards.getActiveBlockPlaceLevel(this, player, location);
  }

  /**
   * Returns active level gated by PVP/PVE policy for a specific target.
   */
  default int getActiveDamageLevel(Player attacker, Entity target) {
    return AdaptationRuntimeGuards.getActiveDamageLevel(this, attacker, target);
  }

  /**
   * Resolves a context only when adaptation is active and interact is allowed.
   */
  default BlockActionContext resolveInteractContext(Player player, Location location) {
    return AdaptationRuntimeGuards.resolveInteractContext(this, player, location);
  }

  /**
   * Resolves interact context and applies an additional player requirement.
   */
  default BlockActionContext resolveInteractContext(Player player, Location location, Predicate<Player> requirement) {
    return AdaptationRuntimeGuards.resolveInteractContext(this, player, location, requirement);
  }

  /**
   * Resolves interact context with optional requirement and survival-only
   * gating.
   */
  default BlockActionContext resolveInteractContext(Player player, Location location, Predicate<Player> requirement, boolean survivalOnly) {
    return AdaptationRuntimeGuards.resolveInteractContext(this, player, location, requirement, survivalOnly);
  }

  /**
   * Resolves a context only when adaptation is active and block break is
   * allowed.
   */
  default BlockActionContext resolveBlockBreakContext(Player player, Location location) {
    return AdaptationRuntimeGuards.resolveBlockBreakContext(this, player, location);
  }

  /**
   * Resolves block-break context and applies an additional player requirement.
   */
  default BlockActionContext resolveBlockBreakContext(Player player, Location location, Predicate<Player> requirement) {
    return AdaptationRuntimeGuards.resolveBlockBreakContext(this, player, location, requirement);
  }

  /**
   * Resolves block-break context with optional requirement and survival-only
   * gating.
   */
  default BlockActionContext resolveBlockBreakContext(Player player, Location location, Predicate<Player> requirement, boolean survivalOnly) {
    return AdaptationRuntimeGuards.resolveBlockBreakContext(this, player, location, requirement, survivalOnly);
  }

  /**
   * Resolves a context only when adaptation is active and block place is
   * allowed.
   */
  default BlockActionContext resolveBlockPlaceContext(Player player, Location location) {
    return AdaptationRuntimeGuards.resolveBlockPlaceContext(this, player, location);
  }

  /**
   * Resolves block-place context and applies an additional player requirement.
   */
  default BlockActionContext resolveBlockPlaceContext(Player player, Location location, Predicate<Player> requirement) {
    return AdaptationRuntimeGuards.resolveBlockPlaceContext(this, player, location, requirement);
  }

  /**
   * Resolves block-place context with optional requirement and survival-only
   * gating.
   */
  default BlockActionContext resolveBlockPlaceContext(Player player, Location location, Predicate<Player> requirement, boolean survivalOnly) {
    return AdaptationRuntimeGuards.resolveBlockPlaceContext(this, player, location, requirement, survivalOnly);
  }

  /**
   * Resolves a context requiring both interact and block-break permission.
   */
  default BlockActionContext resolveInteractBreakContext(Player player, Location location) {
    return AdaptationRuntimeGuards.resolveInteractBreakContext(this, player, location);
  }

  /**
   * Resolves interact+break context and applies an additional player
   * requirement.
   */
  default BlockActionContext resolveInteractBreakContext(Player player, Location location, Predicate<Player> requirement) {
    return AdaptationRuntimeGuards.resolveInteractBreakContext(this, player, location, requirement);
  }

  /**
   * Resolves interact+break context with optional requirement and survival-only
   * gating.
   */
  default BlockActionContext resolveInteractBreakContext(Player player, Location location, Predicate<Player> requirement, boolean survivalOnly) {
    return AdaptationRuntimeGuards.resolveInteractBreakContext(this, player, location, requirement, survivalOnly);
  }

  /**
   * Returns a validated main-hand item when adaptation is active and
   * requirement passes.
   */
  default ItemStack readyMainHand(Player player, Predicate<ItemStack> requirement) {
    return AdaptationRuntimeGuards.readyMainHand(this, player, requirement);
  }

  /**
   * Returns a validated active main-hand item without extra requirement
   * checks.
   */
  default ItemStack readyMainHand(Player player) {
    return readyMainHand(player, null);
  }

  /**
   * Resolves melee combat context from an entity-damage event.
   */
  default MeleeContext resolveMeleeContext(EntityDamageByEntityEvent event, Predicate<ItemStack> mainHandRequirement) {
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
  default AttackContext resolveAttackContext(EntityDamageByEntityEvent event, Predicate<ItemStack> mainHandRequirement) {
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
  default ProjectileContext resolveProjectileContext(EntityDamageByEntityEvent event, Predicate<Projectile> projectileRequirement) {
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
  default boolean canAccessChest(Player player, Location chestLocation) {
    return evaluateWorldPolicy(protector -> protector.canAccessChest(player, chestLocation, this));
  }

  /**
   * World-policy check for generic region access at the player's current
   * location.
   */
  default boolean checkRegion(Player player) {
    return evaluateWorldPolicy(protector -> protector.checkRegion(player, player.getLocation(), this));
  }

  private boolean evaluateWorldPolicy(Predicate<Protector> evaluator) {
    long start = System.nanoTime();
    try {
      for (Protector protector : getProtectors()) {
        if (!evaluator.test(protector)) {
          return false;
        }
      }
      return true;
    } finally {
      WorldPolicyLatencyTelemetry.recordNanos(System.nanoTime() - start);
    }
  }

  /**
   * Returns true when this adaptation currently conflicts with another active
   * adaptation.
   */
  default boolean hasUsageConflict(Player p) {
    return AdaptationRuntimeGuards.hasUsageConflict(this, p);
  }

  /**
   * Runtime-ready level check (ownership + enabled + world/protection/conflict
   * checks).
   */
  default int getActiveLevel(Player p) {
    return AdaptationRuntimeGuards.getActiveLevel(this, p);
  }

  /**
   * Ownership check only (learned level > 0), without runtime gating.
   */
  default boolean hasAdaptation(Player p) {
    return getLevel(p) > 0;
  }

  /**
   * Runtime-usable state (ownership + active world/permission/conflict
   * checks).
   */
  default boolean hasActiveAdaptation(Player p) {
    return getActiveLevel(p) > 0;
  }

  /**
   * Raw learned level (ignores runtime gating such as
   * world/protection/conflict).
   */
  default int getLevel(Player p) {
    return AdaptationRuntimeGuards.getLevel(this, p);
  }

  /**
   * Learned level normalized to 0..1.
   */
  default double getLevelPercent(Player p) {
    if (!this.isEnabled()) {
      return 0;
    }
    if (!this.getSkill().isEnabled()) {
      return 0;
    }
    if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
      return 0.0;
    }
    return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), getLevel(p))), 1);
  }

  /**
   * Level normalized to 0..1 using an explicit level value.
   */
  default double getLevelPercent(int p) {
    return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), p)), 1);
  }

  /**
   * Cost for purchasing exactly this level step.
   */
  default int getCostFor(int level) {
    return (int) (Math.max(1, getBaseCost() + (getBaseCost() * (level * getCostFactor())))) + (level == 1 ? getInitialCost() : 0);
  }

  /**
   * Power cost delta for level transitions.
   */
  default int getPowerCostFor(int level, int myLevel) {
    return level - myLevel;
  }

  /**
   * Cumulative cost to move from current level to target level.
   */
  default int getCostFor(int level, int myLevel) {
    if (myLevel >= level) {
      return 0;
    }


    int c = 0;

    for (int i = myLevel + 1; i <= level; i++) {
      c += getCostFor(i);
    }

    return c;
  }

  /**
   * Cumulative refund amount when reducing from current level to target level.
   */
  default int getRefundCostFor(int level, int myLevel) {
    if (myLevel <= level) {
      return 0;
    }

    int c = 0;

    for (int i = level + 1; i <= myLevel; i++) {
      c += getCostFor(i);
    }

    return c;
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
  default BlockFace getBlockFace(Player player, int maxrange) {
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
   * Opens adaptation GUI and optionally applies blacklist permission checks.
   */
  default boolean openGui(Player player, boolean checkPermissions) {
    return AdaptationGuiSupport.openGui(this, player, checkPermissions);
  }

  /**
   * Opens page 0 of this adaptation GUI.
   */
  default void openGui(Player player) {
    AdaptationGuiSupport.openGui(this, player);
  }

  /**
   * Opens a specific page of this adaptation GUI.
   */
  default void openGui(Player player, int page) {
    AdaptationGuiSupport.openGui(this, player, page);
  }

  /**
   * Checks whether permanent learn confirmation is pending for this
   * player/level.
   */
  default boolean isPermanentLearnConfirmationPending(Player player, int level) {
    return AdaptationGuiSupport.isPermanentLearnConfirmationPending(player, this, level);
  }

  /**
   * Consumes pending permanent learn confirmation for this player/level.
   */
  default boolean consumePermanentLearnConfirmation(Player player, int level) {
    return AdaptationGuiSupport.consumePermanentLearnConfirmation(player, this, level);
  }

  /**
   * Unlearns levels from this adaptation.
   */
  default void unlearn(Player player, int lvl, boolean force) {
    AdaptationGuiSupport.unlearn(this, player, lvl, force);
  }

  /**
   * Learns levels into this adaptation.
   */
  default void learn(Player player, int lvl, boolean force) {
    AdaptationGuiSupport.learn(this, player, lvl, force);
  }

  /**
   * Returns true when the provided recipe belongs to this adaptation.
   */
  default boolean isAdaptationRecipe(Recipe recipe) {
    return AdaptationGuiSupport.isAdaptationRecipe(this, recipe);
  }

  /**
   * Generic attack context payload.
   */
  record AttackContext(Player attacker, Entity target, ItemStack mainHand,
                       int level) {
  }

  /**
   * Block-action context payload.
   */
  record BlockActionContext(Player player, Location location, int level) {
  }

  /**
   * Melee-specific context payload.
   */
  record MeleeContext(Player attacker, LivingEntity target, ItemStack mainHand,
                      int level) {
  }

  /**
   * Projectile-specific context payload.
   */
  record ProjectileContext(Player attacker, LivingEntity target,
                           Projectile projectile, int level) {
  }
}
