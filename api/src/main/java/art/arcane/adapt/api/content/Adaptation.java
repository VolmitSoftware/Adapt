package art.arcane.adapt.api.content;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.asset.Icon;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.api.item.MixingRecipe;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * A registered adaptation, as the rest of the server sees it. Authors extend {@link SimpleAdaptation}; this is the
 * view the catalog hands out, and the type helpers take when they act on behalf of an adaptation.
 *
 * <p>Two levels matter. The <em>learned</em> level ({@link #getLevel}) is what the player bought. The
 * <em>active</em> level ({@link #getActiveLevel}) is the learned level when the adaptation may act for the player
 * right now and 0 otherwise: the adaptation and its skill are enabled, the player is alive and switched it on, has
 * the use permission, is in a world that is not blacklisted and not in spectator (or creative, unless the server
 * allows it), the region protections allow it, the player holds no adaptation configured to conflict with it, and
 * {@link #canUse} agrees. Gameplay code asks for the active level.
 *
 * <p>On Folia the level queries answer 0 off the thread that owns the player. The active level is cached per player
 * for one 50 ms window while the learned level stays the same, so the checks behind it run at most once a tick.
 *
 * @param <T> the adaptation's config class
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface Adaptation<T extends AdaptationConfig> extends ContentComponent {
  /**
   * The live config. A reload replaces the object, so read it through this call each time instead of keeping it.
   */
  T getConfig();

  /**
   * The skill this adaptation is registered under.
   *
   * @throws IllegalStateException when that skill is not published through the API yet, which is the case for an
   *                               extension placed on one of Adapt's built-in skills until those move to the public
   *                               base classes; {@link #getSkillId()} answers in every case
   */
  Skill<?> getSkill();

  /** The id of the skill this adaptation is registered under; known from construction on. */
  Id getSkillId();

  /** The icon the adaptation declared, drawn on its tile. */
  Icon getIcon();

  /** The description shown on the adaptation's tile, in the language of the player being rendered for. */
  String getDescription();

  /** The coloured name. */
  String getDisplayName();

  /** The coloured name followed by a level as a roman numeral; a level of 0 or less gives the name alone. */
  String getDisplayName(int level);

  /** The name on its own skill's pages: the short name when it has one, else the display name. */
  String getShortName();

  /** The short name followed by a level as a roman numeral; a level of 0 or less gives the short name alone. */
  String getShortName(int level);

  /** The highest level a player can learn; at least 1. */
  int getMaxLevel();

  /** The config's {@code baseCost}, at least 0; see {@link #costFor(int, int, double, int)}. */
  int getBaseCost();

  /** The config's {@code initialCost}, at least 0: the extra knowledge the first level costs. */
  int getInitialCost();

  /** The config's {@code costFactor}, at least 0; see {@link #costFor(int, int, double, int)}. */
  double getCostFactor();

  /** True when it cannot be unlearned once bought. */
  boolean isPermanent();

  /**
   * The adaptations a player must hold, at a level, before learning this one, parsed from the config's
   * {@code requires}. A malformed entry is logged and left out.
   */
  List<AdaptationRequirement> getRequirements();

  /** Recipes only a player who learned this adaptation can craft. */
  List<RecipeSpec> getRecipes();

  /** The brewing recipes this adaptation registered. */
  List<BrewingRecipe> getBrewingRecipes();

  /** The potion mixes this adaptation lets its learners brew, for recipe views; none by default. */
  @ApiStatus.Experimental
  default List<MixingRecipe> getMixingRecipes() {
    return List.of();
  }

  /** The custom items this adaptation registered. */
  List<CustomItem.Definition<?>> getItems();

  /** Adds the stat lines of one level, in the viewer's language. */
  void addStats(int level, StatLines lines);

  /** Every stat line shown for {@code level}: {@link #addStats} plus anything this kind of adaptation appends. */
  default void addLevelStats(int level, StatLines lines) {
    addStats(level, lines);
  }

  /** The level a player who mutated this adaptation's skill keeps it at, free, while no upgrade fuses it. */
  default int getKeptLevel() {
    return 1;
  }

  /** How much of {@code amount} this adaptation may still pay the player. Upgrades cap it, blocks pay none. */
  default double payableXp(PlayerRef player, double amount) {
    return amount;
  }

  /** Groups of toggle preferences the preference menu shows together; empty for none. */
  default List<PreferenceToggleGroup> getPlayerPreferenceGroups() {
    return List.of();
  }

  /** The value of a preference in effect for the player. */
  <E extends Enum<E>> E preference(PlayerRef player, PlayerPreference<E> preference);

  /**
   * The value of a preference in effect for a player at {@code level} whose saved choice is {@code saved}, under the
   * server's current preference settings. For work that runs while its owner is offline, from the owner's choice and
   * level as last seen; {@code saved} is null when they had made no choice.
   */
  @ApiStatus.Experimental
  <E extends Enum<E>> E preferenceFor(int level, @Nullable String saved, PlayerPreference<E> preference);

  /** Whether a toggle preference is {@link CommonPreferences.Toggle#ON} for the player. */
  default boolean preferenceEnabled(PlayerRef player, PlayerPreference<CommonPreferences.Toggle> preference) {
    return preference(player, preference) == CommonPreferences.Toggle.ON;
  }

  /** Whether the player has this adaptation switched on; false while their data has not loaded. */
  boolean isPlayerEnabled(PlayerRef player);

  /** Called after the player changed one of this adaptation's preferences. */
  default void onPlayerPreferencesChanged(PlayerProfile player) {
  }

  /** Whether the preference menu shows this preference to the player. */
  default boolean isPlayerPreferenceVisible(PlayerProfile player, PlayerPreference<?> preference) {
    return true;
  }

  /** Whether a preference is unavailable on this server, for example because the platform lacks what it needs. */
  default boolean isPlayerPreferenceBlocked(PlayerPreference<?> preference) {
    return false;
  }

  /** Called after the player's learned level changed. */
  default void onLevelChanged(PlayerRef player, int level) {
  }

  /**
   * The learned level, capped by the configured maximum. 0 for null, for a player whose data has not loaded, and
   * while the adaptation or its skill is disabled. An original that a held upgrade fuses reports the level the upgrade
   * lends it.
   */
  int getLevel(@Nullable PlayerRef player);

  /** As {@link #getLevel(PlayerRef)} for the profile's player; 0 for null. */
  int getLevel(@Nullable PlayerProfile profile);

  /** The learned level of an online player by id; 0 when they are offline. */
  int ownerLevel(UUID owner);

  /** Whether the learned level is above 0. */
  default boolean hasAdaptation(PlayerRef player) {
    return getLevel(player) > 0;
  }

  /** The level when the adaptation may act for the player now, else 0; 0 for null or a dead player. */
  int getActiveLevel(@Nullable PlayerRef player);

  /** Whether the active level is above 0. */
  default boolean hasActiveAdaptation(PlayerRef player) {
    return getActiveLevel(player) > 0;
  }

  /** The active level when {@code requirement} also holds, else 0. */
  int getActiveLevel(@Nullable PlayerRef player, Predicate<PlayerRef> requirement);

  /** The active level when the player is in survival, else 0; adventure does not count. */
  int getActiveSurvivalLevel(@Nullable PlayerRef player);

  /** The active level for a player who may be dead; for adaptations that prevent a death. */
  int getActiveDeathLevel(@Nullable PlayerRef player);

  /** The active level when protections let the player interact at {@code location}, else 0. */
  int getActiveInteractLevel(@Nullable PlayerRef player, Loc location);

  /** The active level when protections let the player break a block at {@code location}, else 0. */
  int getActiveBlockBreakLevel(@Nullable PlayerRef player, Loc location);

  /** The active level when protections let the player place a block at {@code location}, else 0. */
  int getActiveBlockPlaceLevel(@Nullable PlayerRef player, Loc location);

  /** The active level when protections let the attacker damage {@code target}. */
  int getActiveDamageLevel(@Nullable PlayerRef attacker, EntityRef target);

  /** The active level of another adaptation of the same skill; 0 when there is none with that id. */
  default int getActiveSiblingLevel(@Nullable PlayerRef player, Id sibling) {
    if (player == null) {
      return 0;
    }
    for (Adaptation<?> candidate : getSkill().getAdaptations()) {
      if (candidate.getId().equals(sibling)) {
        return candidate.getActiveLevel(player);
      }
    }
    return 0;
  }

  /** Learned level as a fraction of the maximum, 0 to 1; 0 while the adaptation or its skill is disabled. */
  default double getLevelPercent(PlayerRef player) {
    if (!isEnabled() || !getSkill().isEnabled()) {
      return 0D;
    }
    return getLevelPercent(getLevel(player));
  }

  /** {@code level} as a fraction of the maximum level, clamped to 0 to 1; 0 when the maximum is 0 or less. */
  default double getLevelPercent(int level) {
    int maxLevel = getMaxLevel();
    if (maxLevel <= 0) {
      return 0D;
    }
    return Math.min(1D, Math.max(0D, (double) level / maxLevel));
  }

  /** Online players who have learned this adaptation, for tick loops. */
  List<PlayerProfile> learnedCandidates(long now);

  /**
   * Runs {@code action} on the thread that owns the player: at once when the caller already owns it, otherwise (on
   * Folia) on the player's region thread later. Nothing runs once this instance is retired. An exception the action
   * throws is logged at verbose level and not rethrown.
   */
  default void withPlayerThread(PlayerRef player, Runnable action) {
    withPlayerThread(player, null, action);
  }

  /** As above, and does nothing when {@code event} is cancelled, checked both before and after any thread hop. */
  void withPlayerThread(PlayerRef player, @Nullable Cancellable event, Runnable action);

  /** Runs {@code action} on the player's thread when, once there, the player's active level is above 0. */
  default void withAdaptedPlayer(PlayerRef player, Runnable action) {
    withAdaptedPlayer(player, null, action);
  }

  /** As above, and does nothing when {@code event} is cancelled. */
  void withAdaptedPlayer(PlayerRef player, @Nullable Cancellable event, Runnable action);

  /** Runs {@code action} with the active level on the player's thread, when that level is above 0. */
  default void withActiveLevel(PlayerRef player, IntConsumer action) {
    withActiveLevel(player, null, action);
  }

  /** As above, and does nothing when {@code event} is cancelled. */
  void withActiveLevel(PlayerRef player, @Nullable Cancellable event, IntConsumer action);

  /**
   * Asks whether the adaptation may act for the player now. False while the player's data has not loaded or they
   * have the adaptation switched off; otherwise fires {@code AdaptationUseEvent} and consults every registered
   * {@code AbilityUsePolicy}. The active level already includes this check.
   */
  boolean canUse(PlayerRef player);

  /**
   * Whether the player may use the adaptation: true for operators, and when the node
   * {@code adapt.use.<namespace>.<path>} is not set for them; otherwise the node's value.
   */
  boolean hasUsePermission(PlayerRef player);

  /**
   * Grants XP in the adaptation's skill, credited to this adaptation, at the player's position.
   *
   * <p>Every {@code xp} and {@code xpSilent} variant does nothing when the player has the adaptation switched off or
   * the amount is not a finite number above 0. The amount is capped by {@link #payableXp}, and the usage baseline of
   * {@link #awardUsageBaselineXp} is added when it is due.
   */
  default void xp(PlayerRef player, double amount) {
    xp(player, null, amount, null);
  }

  /** As {@link #xp(PlayerRef, double)}; {@code rewardKey} names what was rewarded within this adaptation. */
  default void xp(PlayerRef player, double amount, @Nullable String rewardKey) {
    xp(player, null, amount, rewardKey);
  }

  /** As {@link #xp(PlayerRef, double)}, earned at {@code at}; null means the player's position. */
  default void xp(PlayerRef player, @Nullable Loc at, double amount) {
    xp(player, at, amount, null);
  }

  /**
   * As {@link #xp(PlayerRef, double)}.
   *
   * @param at        where the XP was earned; null means the player's position
   * @param rewardKey names what was rewarded within this adaptation; null or blank counts as {@code use}
   */
  void xp(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey);

  /** As {@link #xp(PlayerRef, double)}, with nothing shown. */
  default void xpSilent(PlayerRef player, double amount) {
    xpSilent(player, amount, null);
  }

  /** As {@link #xp(PlayerRef, Loc, double, String)}, with nothing shown. */
  void xpSilent(PlayerRef player, double amount, @Nullable String rewardKey);

  /**
   * Grants, with nothing shown, the small XP every successful use pays. It is paid at most once per cooldown per
   * player, only to a player whose data has loaded and who has learned the adaptation, and only while the server's
   * usage baseline is switched on.
   */
  void awardUsageBaselineXp(PlayerRef player, int level);

  /**
   * Reads a value this adaptation saved for the player. Values are strings, numbers or booleans. {@code fallback}
   * when nothing is stored under the key, the player's data has not loaded, or they have no record of this
   * adaptation.
   */
  <F> @Nullable F getStorage(PlayerRef player, String key, @Nullable F fallback);

  /**
   * Saves a value for the player with their data; null removes it. False when the player's data has not loaded or
   * they have no record of this adaptation.
   */
  boolean setStorage(PlayerRef player, String key, @Nullable Object value);

  /** The stored value as a string, or {@code fallback} when none is stored. */
  default String getStorageString(PlayerRef player, String key, @Nullable String fallback) {
    Object value = getStorage(player, key, (Object) fallback);
    return value == null ? fallback : String.valueOf(value);
  }

  /** The stored number as an int, or {@code fallback} when the value is missing or not a number. */
  default @Nullable Integer getStorageInt(PlayerRef player, String key, @Nullable Integer fallback) {
    Object value = getStorage(player, key, (Object) fallback);
    return value instanceof Number number ? Integer.valueOf(number.intValue()) : fallback;
  }

  /** The stored number as a long, or {@code fallback} when the value is missing or not a number. */
  default @Nullable Long getStorageLong(PlayerRef player, String key, @Nullable Long fallback) {
    Object value = getStorage(player, key, (Object) fallback);
    return value instanceof Number number ? Long.valueOf(number.longValue()) : fallback;
  }

  /** The stored number as a double, or {@code fallback} when the value is missing or not a number. */
  default @Nullable Double getStorageDouble(PlayerRef player, String key, @Nullable Double fallback) {
    Object value = getStorage(player, key, (Object) fallback);
    return value instanceof Number number ? Double.valueOf(number.doubleValue()) : fallback;
  }

  /** The stored boolean, or {@code fallback} when the value is missing or not a boolean. */
  default @Nullable Boolean getStorageBoolean(PlayerRef player, String key, @Nullable Boolean fallback) {
    Object value = getStorage(player, key, (Object) fallback);
    return value instanceof Boolean flag ? flag : fallback;
  }

  /**
   * Whether the protections this adaptation respects let the player do {@code check} at {@code location}. True when
   * the server has the adaptation respect no protector.
   *
   * @param location where the action happens; null means the victim's position, or the player's without a victim
   * @param victim   the entity about to be damaged, when one is known
   */
  boolean allows(ProtectionCheck check, PlayerRef player, @Nullable Loc location, @Nullable EntityRef victim);

  /** Whether protections let the player break the block at {@code blockLocation}. */
  default boolean canBlockBreak(PlayerRef player, Loc blockLocation) {
    return allows(ProtectionCheck.BLOCK_BREAK, player, blockLocation, null);
  }

  /** Whether protections let the player place a block at {@code blockLocation}. */
  default boolean canBlockPlace(PlayerRef player, Loc blockLocation) {
    return allows(ProtectionCheck.BLOCK_PLACE, player, blockLocation, null);
  }

  /** Whether protections let the player hurt another player standing at {@code victimLocation}. */
  default boolean canPVP(PlayerRef player, Loc victimLocation) {
    return allows(ProtectionCheck.PVP, player, victimLocation, null);
  }

  /** Whether protections let the player hurt a mob standing at {@code victimLocation}. */
  default boolean canPVE(PlayerRef player, Loc victimLocation) {
    return allows(ProtectionCheck.PVE, player, victimLocation, null);
  }

  /** Whether protections let the player hurt {@code victim}, a mob, where it stands. */
  default boolean canPVE(PlayerRef player, EntityRef victim) {
    return allows(ProtectionCheck.PVE, player, victim.location(), victim);
  }

  /** Whether protections let the player interact with the block or entity at {@code targetLocation}. */
  default boolean canInteract(PlayerRef player, Loc targetLocation) {
    return allows(ProtectionCheck.INTERACT, player, targetLocation, null);
  }

  /** Whether protections let the player open the container at {@code chestLocation}. */
  default boolean canAccessChest(PlayerRef player, Loc chestLocation) {
    return allows(ProtectionCheck.ACCESS_CHEST, player, chestLocation, null);
  }

  /** Whether the region the player stands in lets this adaptation act. */
  default boolean checkRegion(PlayerRef player) {
    return allows(ProtectionCheck.REGION, player, player.location(), null);
  }

  /** Whether the region at {@code location} lets this adaptation act for the player. */
  default boolean checkRegion(PlayerRef player, Loc location) {
    return allows(ProtectionCheck.REGION, player, location, null);
  }

  /**
   * Whether the attacker may damage the target: false for a {@linkplain #isProtectedFriendly protected friendly},
   * else {@link #canPVP} for a player target and {@link #canPVE(PlayerRef, EntityRef)} for anything else.
   */
  boolean canDamageTarget(PlayerRef attacker, EntityRef target);

  /**
   * Whether abilities must leave the target alone: a display or interaction entity, an invulnerable entity, one
   * another plugin shields, or a tamed animal whose owner is {@code actor}.
   */
  boolean isProtectedFriendly(@Nullable PlayerRef actor, EntityRef target);

  /** As {@link #isProtectedFriendly}, with the actor given by id; null skips the tamed-animal check. */
  boolean isProtectedFriendlyOwned(@Nullable UUID actor, EntityRef target);

  /** The player, location and active level when the player may interact there, else null. */
  default @Nullable BlockActionContext resolveInteractContext(PlayerRef player, Loc location) {
    return resolveInteractContext(player, location, null, false);
  }

  /** As {@link #resolveInteractContext(PlayerRef, Loc)}, when {@code requirement} also holds for the player. */
  default @Nullable BlockActionContext resolveInteractContext(PlayerRef player, Loc location,
                                                               @Nullable Predicate<PlayerRef> requirement) {
    return resolveInteractContext(player, location, requirement, false);
  }

  /**
   * As {@link #resolveInteractContext(PlayerRef, Loc)}, when {@code requirement} (if any) also holds and, with
   * {@code survivalOnly}, the player is in survival.
   */
  @Nullable BlockActionContext resolveInteractContext(PlayerRef player, Loc location,
                                                       @Nullable Predicate<PlayerRef> requirement,
                                                       boolean survivalOnly);

  /** The player, location and active level when the player may break the block there, else null. */
  default @Nullable BlockActionContext resolveBlockBreakContext(PlayerRef player, Loc location) {
    return resolveBlockBreakContext(player, location, null, false);
  }

  /** As {@link #resolveBlockBreakContext(PlayerRef, Loc)}, when {@code requirement} also holds for the player. */
  default @Nullable BlockActionContext resolveBlockBreakContext(PlayerRef player, Loc location,
                                                                 @Nullable Predicate<PlayerRef> requirement) {
    return resolveBlockBreakContext(player, location, requirement, false);
  }

  /**
   * As {@link #resolveBlockBreakContext(PlayerRef, Loc)}, when {@code requirement} (if any) also holds and, with
   * {@code survivalOnly}, the player is in survival.
   */
  @Nullable BlockActionContext resolveBlockBreakContext(PlayerRef player, Loc location,
                                                         @Nullable Predicate<PlayerRef> requirement,
                                                         boolean survivalOnly);

  /** The player, location and active level when the player may place a block there, else null. */
  default @Nullable BlockActionContext resolveBlockPlaceContext(PlayerRef player, Loc location) {
    return resolveBlockPlaceContext(player, location, null, false);
  }

  /** As {@link #resolveBlockPlaceContext(PlayerRef, Loc)}, when {@code requirement} also holds for the player. */
  default @Nullable BlockActionContext resolveBlockPlaceContext(PlayerRef player, Loc location,
                                                                 @Nullable Predicate<PlayerRef> requirement) {
    return resolveBlockPlaceContext(player, location, requirement, false);
  }

  /**
   * As {@link #resolveBlockPlaceContext(PlayerRef, Loc)}, when {@code requirement} (if any) also holds and, with
   * {@code survivalOnly}, the player is in survival.
   */
  @Nullable BlockActionContext resolveBlockPlaceContext(PlayerRef player, Loc location,
                                                         @Nullable Predicate<PlayerRef> requirement,
                                                         boolean survivalOnly);

  /** As the interact context, also requiring that the player may break the block. */
  default @Nullable BlockActionContext resolveInteractBreakContext(PlayerRef player, Loc location) {
    return resolveInteractBreakContext(player, location, null, false);
  }

  /**
   * As {@link #resolveInteractBreakContext(PlayerRef, Loc)}, when {@code requirement} (if any) also holds and, with
   * {@code survivalOnly}, the player is in survival.
   */
  @Nullable BlockActionContext resolveInteractBreakContext(PlayerRef player, Loc location,
                                                            @Nullable Predicate<PlayerRef> requirement,
                                                            boolean survivalOnly);

  /** The attacker, living target, weapon and active level of a melee hit this adaptation may act on, else null. */
  default @Nullable MeleeContext resolveMeleeContext(EntityDamageByEntityEvent event) {
    return resolveMeleeContext(event, null);
  }

  /**
   * As {@link #resolveMeleeContext(EntityDamageByEntityEvent)}, when the main-hand item also passes {@code mainHand};
   * with a predicate, an empty hand gives null.
   */
  @Nullable MeleeContext resolveMeleeContext(EntityDamageByEntityEvent event, @Nullable Predicate<ItemRef> mainHand);

  /**
   * The attacker, target, weapon and active level of a direct player attack this adaptation may act on, living target
   * or not, else null. Only plain and sweep attacks count, and none while an ability's own damage is being dealt. The
   * target must pass {@link #canDamageTarget}.
   */
  default @Nullable AttackContext resolveAttackContext(EntityDamageByEntityEvent event) {
    return resolveAttackContext(event, null);
  }

  /**
   * As {@link #resolveAttackContext(EntityDamageByEntityEvent)}, when the main-hand item also passes
   * {@code mainHand}; with a predicate, an empty hand gives null.
   */
  @Nullable AttackContext resolveAttackContext(EntityDamageByEntityEvent event, @Nullable Predicate<ItemRef> mainHand);

  /**
   * The shooter, living target, projectile and active level of a hit by a projectile a player shot, else null. The
   * target must pass {@link #canDamageTarget}.
   */
  default @Nullable ProjectileContext resolveProjectileContext(EntityDamageByEntityEvent event) {
    return resolveProjectileContext(event, null);
  }

  /** As {@link #resolveProjectileContext(EntityDamageByEntityEvent)}, when the projectile also passes the predicate. */
  @Nullable ProjectileContext resolveProjectileContext(EntityDamageByEntityEvent event,
                                                        @Nullable Predicate<ProjectileRef> projectile);

  /**
   * Takes a cost of an ability. A registered {@code AbilityCostProvider} may replace or waive it; otherwise
   * {@code defaultCost} runs and takes it the adaptation's own way.
   *
   * @param costKey names the cost within the adaptation, such as {@code cast}
   * @param unit    one unit of the item, for an item cost
   * @return false when the cost could not be paid; the ability must not fire
   */
  boolean payCost(PlayerRef player, String costKey, AbilityCostKind kind, @Nullable ItemRef unit, int amount,
                  AbilityDefaultCost defaultCost);

  /** {@link #payCost} for {@code amount} items like {@code unit}. */
  default boolean payItemCost(PlayerRef player, String costKey, ItemRef unit, int amount, AbilityDefaultCost defaultCost) {
    return payCost(player, costKey, AbilityCostKind.ITEM, unit, amount, defaultCost);
  }

  /** {@link #payCost} for {@code amount} food points. */
  default boolean payHungerCost(PlayerRef player, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return payCost(player, costKey, AbilityCostKind.HUNGER, null, amount, defaultCost);
  }

  /** {@link #payCost} for {@code amount} health. */
  default boolean payHealthCost(PlayerRef player, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return payCost(player, costKey, AbilityCostKind.HEALTH, null, amount, defaultCost);
  }

  /** {@link #payCost} for {@code amount} durability. */
  default boolean payDurabilityCost(PlayerRef player, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return payCost(player, costKey, AbilityCostKind.DURABILITY, null, amount, defaultCost);
  }

  /** {@link #payCost} for {@code amount} vanilla experience. */
  default boolean payExperienceCost(PlayerRef player, String costKey, int amount, AbilityDefaultCost defaultCost) {
    return payCost(player, costKey, AbilityCostKind.VANILLA_EXPERIENCE, null, amount, defaultCost);
  }

  /**
   * Takes a cost that is only final once the ability went through. Finish it with {@link #settleCost} or give it
   * back with {@link #refundCost}, using the charge's activation id.
   *
   * <p>Only item and vanilla experience costs are held back this way. Hunger, health and durability costs are taken
   * at once, as {@link #payCost} takes them, and the returned charge only reports whether that worked.
   */
  AbilityCharge payCostDeferred(PlayerRef player, String costKey, AbilityCostKind kind, @Nullable ItemRef unit,
                                int amount, AbilityDefaultCost defaultCost);

  /** {@link #payCostDeferred} for {@code amount} items like {@code unit}. */
  default AbilityCharge payItemCostDeferred(PlayerRef player, String costKey, ItemRef unit, int amount,
                                            AbilityDefaultCost defaultCost) {
    return payCostDeferred(player, costKey, AbilityCostKind.ITEM, unit, amount, defaultCost);
  }

  /** Makes a deferred charge final; false when no charge is held under {@code activationId}. */
  boolean settleCost(UUID activationId);

  /** Gives a deferred charge back to the player. */
  boolean refundCost(UUID activationId, AbilityRefundReason reason);

  /** Knowledge for exactly this level step. */
  default int getCostFor(int level) {
    return costFor(getBaseCost(), getInitialCost(), getCostFactor(), level);
  }

  /** Knowledge to go from {@code fromLevel} up to {@code level}; also what going back down refunds. */
  default int getCostFor(int level, int fromLevel) {
    long total = 0L;
    for (int step = fromLevel + 1; step <= level; step++) {
      total += getCostFor(step);
      if (total >= Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
    }
    return (int) total;
  }

  /** Power for a level change: 1 to start holding the adaptation, -1 to drop it, 0 for any other change. */
  default int getPowerCostFor(int level, int fromLevel) {
    boolean holds = fromLevel > 0;
    boolean willHold = level > 0;
    if (holds == willHold) {
      return 0;
    }
    return willHold ? 1 : -1;
  }

  /** The cost formula every priced ability uses: {@code base + base * level * factor}, plus {@code initial} at level 1. */
  static int costFor(int baseCost, int initialCost, double costFactor, int level) {
    double scaledCost = Math.max(1D, baseCost + (baseCost * (level * costFactor)));
    double totalCost = scaledCost + (level == 1 ? initialCost : 0);
    if (!Double.isFinite(totalCost) || totalCost >= Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return Math.max(1, (int) totalCost);
  }

  /** True when the recipe belongs to this adaptation and both it and its skill are enabled. */
  default boolean isAdaptationRecipe(Id recipe) {
    if (recipe == null || !isEnabled() || !getSkill().isEnabled()) {
      return false;
    }
    for (RecipeSpec candidate : getRecipes()) {
      if (recipe.equals(candidate.key())) {
        return true;
      }
    }
    return false;
  }

  /** True when {@code recipe} is the id of one of this adaptation's brewing recipes. */
  default boolean isBrewingRecipe(Id recipe) {
    for (BrewingRecipe candidate : getBrewingRecipes()) {
      if (candidate.id().equals(recipe)) {
        return true;
      }
    }
    return false;
  }

  /**
   * A player attack this adaptation may act on.
   *
   * @param mainHand the attacker's main-hand item; null when the hand is empty and no main-hand predicate was given
   * @param level    the attacker's active level
   */
  record AttackContext(PlayerRef attacker, EntityRef target, ItemRef mainHand, int level) {
  }

  /**
   * A block action this adaptation may perform.
   *
   * @param level the player's active level
   */
  record BlockActionContext(PlayerRef player, Loc location, int level) {
  }

  /**
   * A melee hit on a living target this adaptation may act on.
   *
   * @param mainHand the attacker's main-hand item; null when the hand is empty and no main-hand predicate was given
   * @param level    the attacker's active level
   */
  record MeleeContext(PlayerRef attacker, LivingRef target, ItemRef mainHand, int level) {
  }

  /**
   * A projectile hit on a living target this adaptation may act on.
   *
   * @param attacker the player who shot the projectile
   * @param level    the attacker's active level
   */
  record ProjectileContext(PlayerRef attacker, LivingRef target, ProjectileRef projectile, int level) {
  }
}
