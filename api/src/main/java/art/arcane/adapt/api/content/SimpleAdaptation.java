package art.arcane.adapt.api.content;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.asset.Icon;
import art.arcane.adapt.api.internal.AdaptationRuntime;
import art.arcane.adapt.api.internal.ComponentRuntime;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.LevelGate;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.platform.entity.EntityRef;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The base class of every adaptation, built-in or not.
 *
 * <pre>{@code
 * public final class Transmute extends SimpleAdaptation<Transmute.Config> {
 *   public static final Id ID = Id.of("alchemist", "alchemy/transmute");
 *
 *   public Transmute() {
 *     super(ID);
 *     registerConfiguration(Config.class);
 *     setIcon(Id.minecraft("gold_nugget"));
 *   }
 *
 *   @Override
 *   public void addStats(int level, StatLines lines) {
 *     statLore(lines, Format.pc(chance(level), 0), 1);
 *   }
 *
 *   @Handle
 *   public void on(BlockDropItemEvent event) {
 *     PlayerRef player = event.player();
 *     if (hasActiveAdaptation(player) && ThreadLocalRandom.current().nextDouble() < chance(getLevel(player))) {
 *       Vec3 at = event.block().center().position();
 *       event.items().add(event.block().world().createItemEntity(at, PhilosophersStone.DEFINITION.create(1)));
 *     }
 *   }
 *
 *   private double chance(int level) {
 *     return getConfig().chancePerLevel * level;
 *   }
 *
 *   public static final class Config extends AdaptationConfig {
 *     @ConfigDoc("Chance per level to transmute a drop.")
 *     public double chancePerLevel = 0.04D;
 *
 *     public Config() {
 *       baseCost = 3;
 *       maxLevel = 5;
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Which skill the adaptation belongs to is decided by where it is registered, never read from its id. Price,
 * level cap, permanence and requirements live in the config, so an admin can change them.
 *
 * <p>Handlers on a skill or adaptation never see events another handler cancelled unless marked
 * {@link ReceiveCancelledEvents}, and never see the probe events a protection check fires.
 *
 * @param <T> the config class
 */
@ApiStatus.AvailableSince("1.0.0")
public abstract non-sealed class SimpleAdaptation<T extends AdaptationConfig> extends SimpleComponent<T>
    implements Adaptation<T> {
  private final AdaptationRuntime runtime;
  private final List<RecipeSpec> recipes = new ArrayList<>();
  private final List<BrewingRecipe> brewingRecipes = new ArrayList<>();
  private final List<CustomItem.Definition<?>> items = new ArrayList<>();
  private Icon icon = Icon.item(Id.minecraft("paper"));
  private String localizationKey;
  private Text nameText;
  private Text descriptionText;

  /**
   * @param id the adaptation's id, in the namespace of the addon that registers it
   * @throws IllegalStateException when called outside a supplier Adapt is running
   */
  protected SimpleAdaptation(Id id) {
    super(ContentIds.require(id, "adaptation"));
    this.localizationKey = TextKeys.adaptation(id);
    this.runtime = Engine.bridge().attach(this, hooks());
  }

  @Override
  final ComponentRuntime runtime() {
    return runtime;
  }

  @Override
  final String textKey() {
    return localizationKey;
  }

  @Override
  final @Nullable String displayNameForEngine() {
    return localizedName();
  }

  @Override
  final boolean learnerBoundTicking() {
    return usesLearnerBoundTicking();
  }

  @Override
  final int effectiveLevelForEngine(PlayerRef player, int activeLevel) {
    return Math.max(0, Math.min(getMaxLevel(), effectiveLevel(player, activeLevel)));
  }

  /**
   * The level this adaptation acts at for {@code player} when more than the learned level decides it, such as an
   * upgrade the player holds that runs it higher. {@code activeLevel} is the active level Adapt resolved from what
   * the player learned: 0 when the player has not learned it or it may not act now. The result, kept within 0 and
   * {@link #getMaxLevel}, is what {@link #getActiveLevel}, {@link #getActiveSurvivalLevel},
   * {@link #getActiveDeathLevel}, {@link #hasActiveAdaptation}, {@link #withAdaptedPlayer} and
   * {@link #withActiveLevel} see, and a player who has not learned the adaptation still receives its move and jump
   * handlers while this returns more than 0. A raised level still answers to the requirement or game mode the query
   * names. Queries tied to a block or a target ({@link #getActiveBlockBreakLevel}, {@link #getActiveDamageLevel} and
   * the like) keep the resolved level, because their protection checks decide them. An override should read other
   * adaptations through their active level, so their own checks apply. Default: {@code activeLevel}.
   */
  protected int effectiveLevel(PlayerRef player, int activeLevel) {
    return activeLevel;
  }

  /** A message of this adaptation: {@code text("hud.ready")} is {@code <adaptation key>.hud.ready}. */
  protected final Text text(String relativeKey) {
    return Text.key(localizationKey + "." + relativeKey);
  }

  /** Replaces the conventional key prefix of every message of this adaptation. */
  public final void setLocalizationKey(String keyPrefix) {
    this.localizationKey = Objects.requireNonNull(keyPrefix, "keyPrefix");
  }

  /**
   * The prefix every message key of this adaptation starts with: the one from {@link TextKeys#adaptation} unless
   * {@link #setLocalizationKey} replaced it.
   */
  public final String getLocalizationKey() {
    return localizationKey;
  }

  /** Replaces the conventional {@code .name} message. */
  public final void setNameText(Text text) {
    this.nameText = text;
  }

  /** Replaces the conventional {@code .description} message. */
  public final void setDescriptionText(Text text) {
    this.descriptionText = text;
  }

  /** The name in the viewer's language, or null to fall back to the id. Override for a computed name. */
  protected @Nullable String localizedName() {
    return nameText == null ? runtime.localizedName() : nameText.resolve();
  }

  /** Sets the icon drawn on the adaptation's tile; paper until set. */
  public final void setIcon(Icon icon) {
    this.icon = Objects.requireNonNull(icon, "icon");
  }

  /** An item icon whose sprite is looked up by convention: {@code <namespace>:gui/adaptation/<path>}. */
  public final void setIcon(Id item) {
    setIcon(Icon.item(item).sprite(Id.of(getId().namespace(), "gui/adaptation/" + getId().path())));
  }

  @Override
  public final Icon getIcon() {
    return icon;
  }

  /** Registers a recipe only players who learned this adaptation can craft. */
  public final void registerRecipe(RecipeSpec recipe) {
    recipes.add(Objects.requireNonNull(recipe, "recipe"));
  }

  @Override
  public final List<RecipeSpec> getRecipes() {
    return Collections.unmodifiableList(recipes);
  }

  /** Registers a brewing recipe of this adaptation. */
  public final void registerBrewingRecipe(BrewingRecipe recipe) {
    brewingRecipes.add(Objects.requireNonNull(recipe, "recipe"));
  }

  @Override
  public final List<BrewingRecipe> getBrewingRecipes() {
    return Collections.unmodifiableList(brewingRecipes);
  }

  /**
   * Registers a custom item this adaptation makes or uses. Several adaptations may register the same definition.
   *
   * @return the definition, so it can be registered and assigned in one line
   */
  protected final <D> CustomItem.Definition<D> registerItem(CustomItem.Definition<D> definition) {
    items.add(Objects.requireNonNull(definition, "definition"));
    return definition;
  }

  @Override
  public final List<CustomItem.Definition<?>> getItems() {
    return Collections.unmodifiableList(items);
  }

  /**
   * True for an adaptation whose tick only matters while a learner is online. Adapt then skips its ticks when
   * nobody online has learned it.
   */
  protected boolean usesLearnerBoundTicking() {
    return false;
  }

  /** False only with {@link #usesLearnerBoundTicking()}, while no learner is online and no burst runs. */
  @Override
  public boolean hasTickDemand() {
    return !usesLearnerBoundTicking() || isBursting() || runtime.hasOnlineLearner();
  }

  @Override
  public final Skill<?> getSkill() {
    Skill<?> skill = runtime.skill();
    if (skill == null) {
      throw new IllegalStateException(getName() + " is registered under " + runtime.skillId()
          + ", which is not published through the API; use getSkillId()");
    }
    return skill;
  }

  @Override
  public final Id getSkillId() {
    return runtime.skillId();
  }

  /** By default only the {@link CommonPreferences#ENABLED} switch; override to offer more. */
  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED);
  }

  /** The config's {@code enabled} flag. */
  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  /** The config's {@code permanent} flag. */
  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  /** The config's {@code maxLevel}, at least 1. */
  @Override
  public int getMaxLevel() {
    return Math.max(1, getConfig().maxLevel);
  }

  @Override
  public int getBaseCost() {
    return Math.max(0, getConfig().baseCost);
  }

  @Override
  public int getInitialCost() {
    return Math.max(0, getConfig().initialCost);
  }

  @Override
  public double getCostFactor() {
    double factor = getConfig().costFactor;
    return Double.isFinite(factor) ? Math.max(0D, factor) : 0D;
  }

  @Override
  public final List<AdaptationRequirement> getRequirements() {
    return runtime.requirements();
  }

  /**
   * The text set by {@link #setDescriptionText}, else the {@code .description} message, else a "no description"
   * placeholder.
   */
  @Override
  public String getDescription() {
    return descriptionText == null ? runtime.description() : descriptionText.resolve();
  }

  /** The {@linkplain #localizedName() name} in the skill's colour, without a level. */
  @Override
  public String getDisplayName() {
    return runtime.displayName(0);
  }

  @Override
  public final String getDisplayName(int level) {
    return runtime.displayName(level);
  }

  /** The {@code .short_name} message in the skill's colour, or the display name when no bundle declares one. */
  @Override
  public String getShortName() {
    return runtime.shortName(0);
  }

  @Override
  public final String getShortName(int level) {
    return runtime.shortName(level);
  }

  @Override
  public final <E extends Enum<E>> E preference(PlayerRef player, PlayerPreference<E> preference) {
    return runtime.preference(player, preference);
  }

  @Override
  public final <E extends Enum<E>> E preferenceFor(int level, @Nullable String saved, PlayerPreference<E> preference) {
    return runtime.preferenceFor(level, saved, preference);
  }

  @Override
  public final boolean isPlayerEnabled(PlayerRef player) {
    return runtime.isPlayerEnabled(player);
  }

  @Override
  public final int getLevel(@Nullable PlayerRef player) {
    return runtime.level(player);
  }

  @Override
  public int getLevel(@Nullable PlayerProfile profile) {
    return runtime.level(profile);
  }

  @Override
  public final int ownerLevel(UUID owner) {
    return getLevel(Engine.bridge().players().find(owner).orElse(null));
  }

  @Override
  public final int getActiveLevel(@Nullable PlayerRef player) {
    return runtime.activeLevel(player, LevelGate.NONE, null, null, null, false);
  }

  @Override
  public final int getActiveLevel(@Nullable PlayerRef player, Predicate<PlayerRef> requirement) {
    return runtime.activeLevel(player, LevelGate.NONE, null, null, requirement, false);
  }

  @Override
  public final int getActiveSurvivalLevel(@Nullable PlayerRef player) {
    return runtime.activeLevel(player, LevelGate.NONE, null, null, null, true);
  }

  @Override
  public final int getActiveDeathLevel(@Nullable PlayerRef player) {
    return runtime.activeLevel(player, LevelGate.DEATH, null, null, null, false);
  }

  @Override
  public final int getActiveInteractLevel(@Nullable PlayerRef player, Loc location) {
    return runtime.activeLevel(player, LevelGate.INTERACT, location, null, null, false);
  }

  @Override
  public final int getActiveBlockBreakLevel(@Nullable PlayerRef player, Loc location) {
    return runtime.activeLevel(player, LevelGate.BLOCK_BREAK, location, null, null, false);
  }

  @Override
  public final int getActiveBlockPlaceLevel(@Nullable PlayerRef player, Loc location) {
    return runtime.activeLevel(player, LevelGate.BLOCK_PLACE, location, null, null, false);
  }

  @Override
  public final int getActiveDamageLevel(@Nullable PlayerRef attacker, EntityRef target) {
    return runtime.activeLevel(attacker, LevelGate.DAMAGE, null, target, null, false);
  }

  @Override
  public final List<PlayerProfile> learnedCandidates(long now) {
    return runtime.learnedCandidates(now);
  }

  @Override
  public final void withPlayerThread(PlayerRef player, @Nullable Cancellable event, Runnable action) {
    runtime.withPlayerThread(player, event, action);
  }

  @Override
  public final void withAdaptedPlayer(PlayerRef player, @Nullable Cancellable event, Runnable action) {
    runtime.withAdaptedPlayer(player, event, action);
  }

  @Override
  public final void withActiveLevel(PlayerRef player, @Nullable Cancellable event, IntConsumer action) {
    runtime.withActiveLevel(player, event, action);
  }

  @Override
  public final boolean canUse(PlayerRef player) {
    return runtime.canUse(player);
  }

  @Override
  public final boolean hasUsePermission(PlayerRef player) {
    return runtime.hasUsePermission(player);
  }

  @Override
  public final void xp(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey) {
    runtime.xp(player, at, amount, rewardKey, false);
  }

  @Override
  public final void xpSilent(PlayerRef player, double amount, @Nullable String rewardKey) {
    runtime.xp(player, null, amount, rewardKey, true);
  }

  @Override
  public final void awardUsageBaselineXp(PlayerRef player, int level) {
    runtime.awardUsageBaselineXp(player, level);
  }

  @Override
  public final <F> @Nullable F getStorage(PlayerRef player, String key, @Nullable F fallback) {
    return runtime.storage(player, key, fallback);
  }

  @Override
  public final boolean setStorage(PlayerRef player, String key, @Nullable Object value) {
    return runtime.setStorage(player, key, value);
  }

  @Override
  public final boolean allows(ProtectionCheck check, PlayerRef player, @Nullable Loc location,
                              @Nullable EntityRef victim) {
    return runtime.allows(check, player, location, victim);
  }

  @Override
  public final boolean canDamageTarget(PlayerRef attacker, EntityRef target) {
    return runtime.canDamageTarget(attacker, target);
  }

  @Override
  public final boolean isProtectedFriendly(@Nullable PlayerRef actor, EntityRef target) {
    return runtime.isProtectedFriendly(actor, target);
  }

  @Override
  public final boolean isProtectedFriendlyOwned(@Nullable UUID actor, EntityRef target) {
    return runtime.isProtectedFriendlyOwned(actor, target);
  }

  @Override
  public final @Nullable BlockActionContext resolveInteractContext(PlayerRef player, Loc location,
                                                                    @Nullable Predicate<PlayerRef> requirement,
                                                                    boolean survivalOnly) {
    return blockContext(player, location, LevelGate.INTERACT, requirement, survivalOnly);
  }

  @Override
  public final @Nullable BlockActionContext resolveBlockBreakContext(PlayerRef player, Loc location,
                                                                      @Nullable Predicate<PlayerRef> requirement,
                                                                      boolean survivalOnly) {
    return blockContext(player, location, LevelGate.BLOCK_BREAK, requirement, survivalOnly);
  }

  @Override
  public final @Nullable BlockActionContext resolveBlockPlaceContext(PlayerRef player, Loc location,
                                                                      @Nullable Predicate<PlayerRef> requirement,
                                                                      boolean survivalOnly) {
    return blockContext(player, location, LevelGate.BLOCK_PLACE, requirement, survivalOnly);
  }

  @Override
  public final @Nullable BlockActionContext resolveInteractBreakContext(PlayerRef player, Loc location,
                                                                         @Nullable Predicate<PlayerRef> requirement,
                                                                         boolean survivalOnly) {
    return blockContext(player, location, LevelGate.INTERACT_BREAK, requirement, survivalOnly);
  }

  @Override
  public final @Nullable MeleeContext resolveMeleeContext(EntityDamageByEntityEvent event,
                                                           @Nullable Predicate<ItemRef> mainHand) {
    return runtime.meleeContext(event, mainHand);
  }

  @Override
  public final @Nullable AttackContext resolveAttackContext(EntityDamageByEntityEvent event,
                                                             @Nullable Predicate<ItemRef> mainHand) {
    return runtime.attackContext(event, mainHand);
  }

  @Override
  public final @Nullable ProjectileContext resolveProjectileContext(EntityDamageByEntityEvent event,
                                                                     @Nullable Predicate<ProjectileRef> projectile) {
    return runtime.projectileContext(event, projectile);
  }

  @Override
  public final boolean payCost(PlayerRef player, String costKey, AbilityCostKind kind, @Nullable ItemRef unit,
                               int amount, AbilityDefaultCost defaultCost) {
    return runtime.charge(player, costKey, kind, unit, amount, defaultCost);
  }

  @Override
  public final AbilityCharge payCostDeferred(PlayerRef player, String costKey, AbilityCostKind kind,
                                             @Nullable ItemRef unit, int amount, AbilityDefaultCost defaultCost) {
    return runtime.chargeDeferred(player, costKey, kind, unit, amount, defaultCost);
  }

  @Override
  public final boolean settleCost(UUID activationId) {
    return runtime.settle(activationId);
  }

  @Override
  public final boolean refundCost(UUID activationId, AbilityRefundReason reason) {
    return runtime.refund(activationId, reason);
  }

  /** Damages the main-hand item as a durability cost, so a cost provider can replace it. */
  @Override
  public final void damageHand(PlayerRef player, int damage) {
    payDurabilityCost(player, "durability", damage, () -> {
      runtime.damageItem(player, false, damage);
      return true;
    });
  }

  /** Damages the off-hand item as a durability cost, so a cost provider can replace it. */
  @Override
  public final void damageOffHand(PlayerRef player, int damage) {
    payDurabilityCost(player, "durability", damage, () -> {
      runtime.damageItem(player, true, damage);
      return true;
    });
  }

  /**
   * Calls {@code pulse} for each online player who has learned this adaptation, about once every
   * {@code cadenceMillis} per player, on that player's thread. The cadence is read again before each pulse, so a
   * config value can change it live; values below 50 count as 50. Adapt spreads players over ticks and skips players
   * whose data is not loaded. Call it from the constructor; the pulse stops when the adaptation unregisters.
   *
   * <p>A player is pulsed on the first visit after they qualify, then once per cadence. Learned is enough: the pulse
   * does not check the active level. Nobody is pulsed while the adaptation is disabled, and a player who unlearns it
   * or leaves starts over. An exception the pulse throws is logged without stopping later pulses.
   */
  protected final void pulseOwners(LongSupplier cadenceMillis, Consumer<PlayerRef> pulse) {
    runtime.pulseOwners(Objects.requireNonNull(cadenceMillis, "cadenceMillis"), Objects.requireNonNull(pulse, "pulse"));
  }

  /**
   * As {@link #pulseOwners(LongSupplier, Consumer)}, for an adaptation that also keeps working for players who have
   * not learned it, such as players it left a bonus on. {@code extraDemand} keeps the pulse running while nobody
   * online has learned the adaptation; {@code interest} decides, by UUID, which online players are pulsed, in place
   * of "has learned it" (use {@link #ownerLevel} for that part). Both are asked often, so keep them cheap.
   */
  @ApiStatus.Experimental
  protected final void pulseOwners(LongSupplier cadenceMillis, BooleanSupplier extraDemand,
                                   Predicate<UUID> interest, Consumer<PlayerRef> pulse) {
    runtime.pulseOwners(Objects.requireNonNull(cadenceMillis, "cadenceMillis"),
        Objects.requireNonNull(extraDemand, "extraDemand"), Objects.requireNonNull(interest, "interest"),
        Objects.requireNonNull(pulse, "pulse"));
  }

  /**
   * The player's active level of this adaptation itself, leaving out a level a fusing upgrade lends it; 0 for
   * {@code null}.
   */
  @ApiStatus.Experimental
  protected final int ownActiveLevel(@Nullable PlayerRef player) {
    return runtime.ownActiveLevel(player);
  }

  /**
   * Asks the player to repeat an action before it runs: the first call tells them to do it again and returns false,
   * and a second call for the same action and the same items within 5 seconds returns true. False for a player
   * offline or on another thread.
   *
   * @param action what is being confirmed, such as {@code "unbind"}; a different action starts over
   * @param items the stacks the action works on; a change to any of them starts over
   */
  @ApiStatus.Experimental
  protected final boolean confirm(PlayerRef player, String action, @Nullable ItemRef... items) {
    return runtime.confirm(Objects.requireNonNull(player, "player"), Objects.requireNonNull(action, "action"),
        items == null ? new ItemRef[0] : items);
  }

  /**
   * Damages the player as a health cost of this adaptation: no knockback, and a cost provider can replace it. Does
   * nothing for a dead player or an amount that is not a finite number above 0; the cost is quoted as the amount
   * rounded up.
   */
  protected final void applyPlayerDamage(PlayerRef player, double amount) {
    if (!Double.isFinite(amount) || amount <= 0D || player.isDead()) {
      return;
    }
    payHealthCost(player, "health", (int) Math.ceil(amount), () -> {
      runtime.applyPlayerDamage(player, amount);
      return true;
    });
  }

  /** Adds the stat line {@code + <value> <label>}, the label being this adaptation's {@code lore<index>} message. */
  protected final void statLore(StatLines lines, Object value, int loreIndex) {
    statLore(lines, TextFormat.GREEN, "+ ", value, TextFormat.GRAY, loreIndex);
  }

  /** As {@link #statLore(StatLines, Object, int)} with a chosen prefix and its colour; the label stays grey. */
  protected final void statLore(StatLines lines, TextFormat prefixColor, String prefix, Object value, int loreIndex) {
    statLore(lines, prefixColor, prefix, value, TextFormat.GRAY, loreIndex);
  }

  /** As {@link #statLore(StatLines, Object, int)} with a chosen prefix, prefix colour and label colour. */
  protected final void statLore(StatLines lines, TextFormat prefixColor, String prefix, Object value,
                                TextFormat labelColor, int loreIndex) {
    statLore(lines, prefixColor, prefix, value, labelColor, text("lore" + loreIndex));
  }

  /** Adds one stat line with a chosen prefix and its colour, labelled by {@code label} in grey. */
  protected final void statLore(StatLines lines, TextFormat prefixColor, String prefix, Object value, Text label) {
    statLore(lines, prefixColor, prefix, value, TextFormat.GRAY, label);
  }

  /**
   * Adds one stat line in the shape every adaptation uses.
   *
   * @param value shown after the prefix; a {@code double} or {@code float} is formatted to two decimals
   */
  protected final void statLore(StatLines lines, TextFormat prefixColor, String prefix, Object value,
                                TextFormat labelColor, Text label) {
    lines.addLore(runtime.statLine(prefixColor + prefix, value, labelColor.toString(), label.resolve()));
  }

  /** Adds a line that names something the level unlocks, without a value. */
  protected static void unlockLore(StatLines lines, Text label) {
    lines.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + label.resolve());
  }

  private @Nullable BlockActionContext blockContext(PlayerRef player, Loc location, LevelGate gate,
                                                    @Nullable Predicate<PlayerRef> requirement, boolean survivalOnly) {
    int level = runtime.activeLevel(player, gate, location, null, requirement, survivalOnly);
    return level > 0 ? new BlockActionContext(player, location, level) : null;
  }
}
