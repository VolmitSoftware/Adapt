package art.arcane.adapt.api.content;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.Milestone;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.internal.ComponentHooks;
import art.arcane.adapt.api.internal.ComponentRuntime;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * What {@link SimpleSkill} and {@link SimpleAdaptation} share: an id, a config, a tick, a lifetime and the helpers
 * both kinds of content use.
 *
 * <p>A component is built by Adapt, from the supplier an addon registered, on the server thread. Its constructor
 * declares what it is; everything it does afterwards happens in {@code @Handle} methods and {@link #onTick()}.
 * Adapt builds a fresh instance after a config reload and retires the old one, so a component must not be kept in
 * a static field or built by hand.
 *
 * @param <T> the config class
 */
@ApiStatus.AvailableSince("1.0.0")
public abstract sealed class SimpleComponent<T> implements ContentComponent permits SimpleSkill, SimpleAdaptation {
  private static final Set<DamageCause> NON_ADAPTABLE_DAMAGE_CAUSES = Set.of(
      DamageCause.VOID,
      DamageCause.LAVA,
      DamageCause.HOT_FLOOR,
      DamageCause.CRAMMING,
      DamageCause.MELTING,
      DamageCause.SUFFOCATION,
      DamageCause.SUICIDE,
      DamageCause.WITHER,
      DamageCause.FLY_INTO_WALL,
      DamageCause.FALL,
      DamageCause.SONIC_BOOM,
      DamageCause.THORNS
  );

  private final Id id;
  private final List<PlatformNeed> platformNeeds = new ArrayList<>();
  private final List<AdaptAdvancement> advancements = new ArrayList<>();
  private final List<Milestone> milestones = new ArrayList<>();
  private Class<T> configType;

  SimpleComponent(Id id) {
    this.id = id;
  }

  abstract ComponentRuntime runtime();

  /** The key every conventional message of this component starts with. */
  abstract String textKey();

  boolean learnerBoundTicking() {
    return false;
  }

  int effectiveLevelForEngine(PlayerRef player, int activeLevel) {
    return activeLevel;
  }

  /** The name the engine shows, or null to fall back to the id. */
  abstract @Nullable String displayNameForEngine();

  @Override
  public final Id getId() {
    return id;
  }

  @Override
  public final String getName() {
    return id.toString();
  }

  @Override
  public final String getOwner() {
    return runtime().owner();
  }

  /**
   * Declares the config class. Call first in the constructor; {@link #getConfig()} works from the next line on.
   */
  public final void registerConfiguration(Class<T> type) {
    this.configType = type;
  }

  /** The class passed to {@link #registerConfiguration}, or null before that call. */
  public final @Nullable Class<T> getConfigurationClass() {
    return configType;
  }

  /**
   * The live config: read from the component's file on first use, written with defaults when the file is missing.
   * When the file cannot be read, the defaults are used in memory and a warning is logged. A reload gives a new
   * object, so read fields through this call each time instead of caching it.
   *
   * @throws IllegalStateException when no configuration class was registered
   */
  public final T getConfig() {
    Class<T> type = configType;
    if (type == null) {
      throw new IllegalStateException(getName() + " registered no configuration class");
    }
    return type.cast(runtime().config(type));
  }

  /**
   * Called with a freshly parsed config before it goes live, to clamp or repair values. Also runs on the defaults
   * before they are written to a new file. During a reload, a throw rejects the parsed config and keeps the previous
   * one live.
   */
  protected void normalizeLoadedConfig(T loadedConfig) {
  }

  /** Called after a config went live, on every load and reload; {@code previousConfig} is null on the first load. */
  protected void onConfigReload(@Nullable T previousConfig, T newConfig) {
  }

  /** Declares what the component needs from the Minecraft version; call from the constructor. */
  protected final void needs(PlatformNeed... needs) {
    platformNeeds.addAll(List.of(needs));
  }

  @Override
  public final List<PlatformNeed> getPlatformNeeds() {
    return Collections.unmodifiableList(platformNeeds);
  }

  /** Adds an advancement to this component's advancement tree; call from the constructor. */
  public final void registerAdvancement(AdaptAdvancement advancement) {
    advancements.add(advancement);
  }

  @Override
  public final List<AdaptAdvancement> getAdvancements() {
    return Collections.unmodifiableList(advancements);
  }

  /** Adds a milestone: an advancement and XP reward granted once a player's stat counter reaches a goal. */
  public final void registerMilestone(Milestone milestone) {
    milestones.add(milestone);
  }

  /** Grants {@code advancementKey} and {@code reward} XP once the player's {@code stat} reaches {@code goal}. */
  protected final void registerMilestone(String advancementKey, String stat, double goal, double reward) {
    registerMilestone(new Milestone(advancementKey, stat, goal, reward));
  }

  /** As above with a reward read when the milestone completes, so it can come from config. */
  protected final void registerMilestone(String advancementKey, String stat, double goal, DoubleSupplier reward) {
    registerMilestone(new Milestone(advancementKey, stat, goal, reward));
  }

  @Override
  public final List<Milestone> getMilestones() {
    return Collections.unmodifiableList(milestones);
  }

  /**
   * Runs every {@link #getInterval()} milliseconds while {@link #hasTickDemand()} is true. Only a class that
   * overrides this is ticked.
   *
   * <p>On Folia this runs on the global region thread. Touch an entity or a block only from a task scheduled on its
   * own region through {@code AdaptApi.scheduler()}.
   */
  public void onTick() {
  }

  /** Whether a tick is worth running now. Return false while nobody could be affected. */
  public boolean hasTickDemand() {
    return true;
  }

  /** Called once when the instance goes live, after its config is available and before its first event. */
  protected void onRuntimeActivated() {
  }

  /** Extra objects whose {@code @Handle} methods live and die with this component. */
  protected List<?> createCompanionListeners() {
    return List.of();
  }

  /**
   * The tick interval in milliseconds; 0 while a {@linkplain #burst burst} runs. It starts at 50 for a skill and
   * 1000 for an adaptation, and a config's {@code setInterval} above 0 replaces it on each load.
   */
  public final long getInterval() {
    return runtime().interval();
  }

  /** Sets the tick interval in milliseconds; values below 50 are raised to 50. */
  public final void setInterval(long millis) {
    runtime().setInterval(millis);
  }

  /**
   * Ticks every server tick for the next {@code ticks} ticks, then returns to the interval. Calls add up; a value of
   * 0 or less does nothing.
   */
  public final void burst(int ticks) {
    runtime().burst(ticks);
  }

  /** Ticks once on the next server tick. */
  public final void retick() {
    runtime().burst(1);
  }

  /** Ends a running burst; ticks return to the interval. */
  public final void stopBursting() {
    runtime().stopBursting();
  }

  /** Whether burst ticks are still owed. */
  public final boolean isBursting() {
    return runtime().isBursting();
  }

  @Override
  public final boolean isRuntimeRegistered() {
    return runtime().isRegistered();
  }

  /**
   * Retires the instance: no more ticks or events. Adapt calls this when the instance is replaced or its addon
   * leaves. Override to release what the instance holds, and call {@code super.unregister()}.
   */
  public void unregister() {
    runtime().unregister();
  }

  @Override
  public boolean areParticlesEnabled() {
    return runtime().particlesEnabled();
  }

  @Override
  public boolean areSoundsEnabled() {
    return runtime().soundsEnabled();
  }

  /** The profile of an online player whose data has loaded, or null. */
  public final @Nullable PlayerProfile getPlayer(@Nullable PlayerRef player) {
    return runtime().profile(player);
  }

  /**
   * Adds to one of the player's stat counters; milestones watching the stat are checked. Does nothing while the
   * player's data has not loaded.
   */
  protected final void addStat(PlayerRef player, String stat, double amount) {
    runtime().addStat(player, stat, amount);
  }

  /** The value of one of the player's stat counters; 0 while their data has not loaded. */
  protected final double statOf(PlayerRef player, String stat) {
    return runtime().stat(player, stat);
  }

  /**
   * Grants an advancement of this component; false when the player already has it, their data has not loaded, or
   * advancements are off.
   */
  protected final boolean grantOnce(PlayerRef player, String advancementKey) {
    return runtime().grantOnce(player, advancementKey);
  }

  /** A map for per-player state. Adapt removes players who leave; call from a field initialiser. */
  protected final <V> Map<UUID, V> playerState() {
    return runtime().playerState();
  }

  /** Cooldown timestamps for this component; call from a field initialiser. */
  protected final Cooldowns cooldowns() {
    return runtime().cooldowns();
  }

  /** An emitter at {@code location} owned by this component; see {@link art.arcane.adapt.api.fx.Fx}. */
  protected final FxEmitter fx(Loc location, FxPriority priority) {
    return runtime().fx(location, priority);
  }

  /** An emitter at {@code entity}'s position now. */
  protected final FxEmitter fx(EntityRef entity, FxPriority priority) {
    return runtime().fx(entity, priority);
  }

  /** A timeline anchored at {@code location}; call {@code start()} when it is configured. */
  protected final FxTimeline timeline(Loc location) {
    return runtime().timeline(location);
  }

  /** A timeline that follows {@code entity}. */
  protected final FxTimeline timeline(EntityRef entity) {
    return runtime().timeline(entity);
  }

  /** One sound at {@code location} at gameplay priority. */
  protected final void sfx(Loc location, Id sound, float volume, float pitch) {
    runtime().sfx(location, sound, volume, pitch);
  }

  /** The item worth of a material, used to scale XP by what was mined, crafted or found. */
  public final double getValue(Id material) {
    return runtime().materialValue(material);
  }

  /** The item worth of the stack's material. */
  public final double getValue(ItemRef item) {
    return runtime().materialValue(item.type());
  }

  /** The item worth of the block's material. */
  public final double getValue(BlockRef block) {
    return runtime().materialValue(block.type());
  }

  /** The item worth of the block state's material. */
  public final double getValue(BlockStateRef block) {
    return runtime().materialValue(block.type());
  }

  /** {@code xp} scaled by what the server's earnings rules say about the block, rounded. */
  public final double blockXP(BlockRef block, double xp) {
    return Math.round(xp * runtime().blockXpMultiplier(block));
  }

  /** The player's armour as a fraction, 0 to 0.8, ignoring pieces a cosmetic covers. */
  public final double getArmorValue(PlayerRef player) {
    return runtime().armorValue(player);
  }

  /** Whether damage of this cause is the kind adaptations react to; false for the void, fall, lava and the like. */
  public final boolean isAdaptableDamageCause(EntityDamageEvent event) {
    return !NON_ADAPTABLE_DAMAGE_CAUSES.contains(event.cause());
  }

  /** Takes one item from the stack in the player's main hand. */
  public final void decrementItemstack(ItemRef hand, PlayerRef player) {
    if (hand.amount() > 1) {
      hand.setAmount(hand.amount() - 1);
      player.inventory().setMainHand(hand);
    } else {
      player.inventory().setMainHand(null);
    }
  }

  /**
   * Gives a potion effect, extending one the player already has: durations add up and the higher amplifier wins.
   *
   * @param overlap when false an active effect of the same type is left untouched
   */
  public final void addPotionStacks(PlayerRef player, Id effect, int amplifier, int durationTicks, boolean overlap) {
    runtime().addPotionStacks(player, effect, amplifier, durationTicks, overlap);
  }

  /**
   * Adds the effect at once, on the calling thread, which must own the player; false when nothing changed, such as an
   * effect already active and {@code overlap} false.
   */
  public final boolean addPotionStacksNow(PlayerRef player, Id effect, int amplifier, int durationTicks,
                                          boolean overlap) {
    return runtime().addPotionStacksNow(player, effect, amplifier, durationTicks, overlap);
  }

  /** Whether {@code player} may pick {@code item} up now: it is valid, not locked, and owned by no one else. */
  public final boolean canSnatchItem(PlayerRef player, ItemEntityRef item) {
    return runtime().canSnatchItem(player, item);
  }

  /** Damages the item in the player's main hand. */
  public void damageHand(PlayerRef player, int damage) {
    runtime().damageItem(player, false, damage);
  }

  /** Damages the item in the player's off hand. */
  public void damageOffHand(PlayerRef player, int damage) {
    runtime().damageItem(player, true, damage);
  }

  /** Moves a dropped item into the player's inventory as a pickup other plugins can refuse; true when it moved. */
  public final boolean safeGiveItem(PlayerRef player, EntityRef itemEntity, ItemRef item) {
    return runtime().giveItem(player, itemEntity, item);
  }

  /** Adds an item to the player's inventory and drops what does not fit at their feet. */
  public final void safeGiveItem(PlayerRef player, ItemRef item) {
    runtime().giveItem(player, item);
  }

  /** Gives vanilla experience points. */
  public final void giveExp(PlayerRef player, int amount) {
    runtime().giveExp(player, amount);
  }

  /** The face of the block the player looks at within {@code maxRange}, or null. */
  public final @Nullable BlockFace getBlockFace(PlayerRef player, int maxRange) {
    return runtime().blockFace(player, maxRange);
  }

  @Override
  public final boolean equals(Object other) {
    return this == other;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + id + "]";
  }

  final ComponentHooks hooks() {
    return new ComponentHooks() {
      @Override
      public void activated() {
        onRuntimeActivated();
      }

      @Override
      public List<?> companionListeners() {
        return createCompanionListeners();
      }

      @Override
      public @Nullable String localizedName() {
        return displayNameForEngine();
      }

      @Override
      public boolean usesLearnerBoundTicking() {
        return learnerBoundTicking();
      }

      @Override
      public int effectiveLevel(PlayerRef player, int activeLevel) {
        return effectiveLevelForEngine(player, activeLevel);
      }

      @Override
      public void normalizeConfig(Object config) {
        normalizeLoadedConfig(configType.cast(config));
      }

      @Override
      public void configReloaded(Object previous, Object current) {
        onConfigReload(previous == null ? null : configType.cast(previous), configType.cast(current));
      }
    };
  }
}
