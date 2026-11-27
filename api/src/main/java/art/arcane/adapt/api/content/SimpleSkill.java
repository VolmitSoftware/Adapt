package art.arcane.adapt.api.content;

import art.arcane.adapt.api.asset.Icon;
import art.arcane.adapt.api.internal.ComponentRuntime;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.SkillRuntime;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The base class of every skill, built-in or not.
 *
 * <pre>{@code
 * public final class AlchemySkill extends SimpleSkill<AlchemySkill.Config> {
 *   private final XpSource gathering = xpSource("gathering");
 *
 *   public AlchemySkill() {
 *     super(AlchemyAddon.ALCHEMY);
 *     registerConfiguration(Config.class);
 *     setColor(Rgb.of(0x7A4FD1));
 *     setIcon(Icon.item(Id.minecraft("brewing_stand")).sprite(Id.of("alchemist", "gui/skill/alchemy")));
 *     registerAdaptation(Transmute.ID, Transmute::new);
 *     registerGroup(text("group.lab"), List.of(Transmute.ID));
 *   }
 *
 *   @Handle(priority = EventPriority.MONITOR)
 *   public void on(BlockBreakEvent event) {
 *     shouldReturnForPlayer(event.player(), event,
 *         () -> gathering.award(event.player(), event.block().center(), getConfig().gatherXp));
 *   }
 *
 *   public static final class Config extends SkillConfig {
 *     @ConfigDoc("XP for each block broken.")
 *     public double gatherXp = 12.0D;
 *   }
 * }
 * }</pre>
 *
 * <p>The constructor declares the skill and must have no side effects in the world: Adapt runs it again after a
 * config reload. The name, description and other strings come from the addon's message bundle by the keys in
 * {@link TextKeys}.
 *
 * @param <T> the config class
 */
@ApiStatus.AvailableSince("1.0.0")
public abstract non-sealed class SimpleSkill<T extends SkillConfig> extends SimpleComponent<T> implements Skill<T> {
  private static final Id PARROT = Id.minecraft("parrot");
  private static final List<String> INVALID_DAMAGEABLE_NAMES = List.of(
      "armor_stand", "item_frame", "glow_item_frame", "painting", "leash_hitch", "leash_knot", "evoker_fangs",
      "marker", "boat", "chest_boat", "minecart");
  private static final List<String> INVALID_DAMAGEABLE_PREFIXES = List.of("minecart_");
  private static final List<String> INVALID_DAMAGEABLE_SUFFIXES = List.of(
      "_minecart", "_boat", "_chest_boat", "_raft", "_chest_raft");

  private final SkillRuntime runtime;
  private final Map<Id, AdaptationEntry> declaredAdaptations = new LinkedHashMap<>();
  private final List<AdaptationGroup> groups = new ArrayList<>();
  private final List<XpSource> xpSources = new ArrayList<>();
  private final List<Text> xpGuide = new ArrayList<>();
  private final List<RecipeSpec> recipes = new ArrayList<>();
  private Rgb color = Rgb.of(0xFFFFFF);
  private Icon icon = Icon.item(Id.minecraft("book"));
  private Id advancementBackground = Id.minecraft("textures/block/deepslate_tiles.png");
  private MutationKit mutationKit;

  /**
   * @param id the skill's id; one an addon registers has one path segment in the addon's namespace, which
   *     {@link art.arcane.adapt.api.addon.ContentRegistrar#skill} checks
   * @throws IllegalStateException when called outside a supplier Adapt is running
   */
  protected SimpleSkill(Id id) {
    super(ContentIds.require(id, "skill"));
    this.runtime = Engine.bridge().attach(this, hooks());
  }

  @Override
  final ComponentRuntime runtime() {
    return runtime;
  }

  @Override
  final String textKey() {
    return TextKeys.skill(getId());
  }

  @Override
  final @Nullable String displayNameForEngine() {
    return runtime.localizedName();
  }

  /** A message of this skill: {@code text("group.lab")} is {@code <namespace>.skill.<path>.group.lab}. */
  protected final Text text(String relativeKey) {
    return Text.key(textKey() + "." + relativeKey);
  }

  /** The skill's colour, used for its name and as the base of a generated theme. Config can override it. */
  public final void setColor(Rgb color) {
    this.color = Objects.requireNonNull(color, "color");
  }

  /** Sets the icon drawn on the skill's tile and pages; a book until set. */
  public final void setIcon(Icon icon) {
    this.icon = Objects.requireNonNull(icon, "icon");
  }

  /** An item icon whose sprite is looked up by convention: {@code <namespace>:gui/skill/<path>}. */
  public final void setIcon(Id item) {
    setIcon(Icon.item(item).sprite(Id.of(getId().namespace(), "gui/skill/" + getId().path())));
  }

  /** The texture behind this skill's tab in the vanilla advancement screen. */
  public final void setAdvancementBackground(Id texture) {
    this.advancementBackground = Objects.requireNonNull(texture, "texture");
  }

  /** The advancement tab texture; {@code minecraft:textures/block/deepslate_tiles.png} until set. */
  public final Id getAdvancementBackground() {
    return advancementBackground;
  }

  /**
   * Adds an adaptation to this skill. Adapt builds it after this constructor returns.
   *
   * @param id      the id the built adaptation must report, in this skill's namespace
   * @param factory builds a fresh adaptation
   * @throws IllegalArgumentException when the id breaks the content grammar or is registered twice
   */
  protected final void registerAdaptation(Id id, Supplier<? extends SimpleAdaptation<?>> factory) {
    ContentIds.require(id, "adaptation");
    Objects.requireNonNull(factory, "factory");
    if (declaredAdaptations.putIfAbsent(id, new AdaptationEntry(id, factory)) != null) {
      throw new IllegalArgumentException(getName() + " registers adaptation " + id + " twice");
    }
  }

  /** The adaptations this skill declared, in order. Adaptations other addons added are not listed here. */
  public final List<AdaptationEntry> getDeclaredAdaptations() {
    return List.copyOf(declaredAdaptations.values());
  }

  /** Adds a labelled row to the skill's pages. Rows appear in call order. */
  protected final void registerGroup(Text label, List<Id> adaptations) {
    groups.add(new AdaptationGroup(label, adaptations));
  }

  @Override
  public final List<AdaptationGroup> getAdaptationGroups() {
    return Collections.unmodifiableList(groups);
  }

  /**
   * Declares a way to earn XP in this skill, described in the guide by {@code <skill key>.xp.<key>}. Call from a
   * field initialiser or the constructor.
   */
  protected final XpSource xpSource(String key) {
    return declareXpSource(key, null);
  }

  /**
   * Declares a way to earn XP in this skill, described in the guide by {@code guide}. Sources declared with equal
   * guide texts share one guide line, shown where the first of them was declared.
   */
  protected final XpSource xpSource(String key, Text guide) {
    return declareXpSource(key, Objects.requireNonNull(guide, "guide"));
  }

  /**
   * Adds a guide line that belongs to no source, such as what pays less or nothing. It appears in declaration order
   * among the source lines, once however often it is added.
   */
  protected final void xpNote(Text note) {
    addXpGuideLine(Objects.requireNonNull(note, "note"));
  }

  @Override
  public final List<XpSource> getXpSources() {
    return Collections.unmodifiableList(xpSources);
  }

  @Override
  public final List<Text> getXpGuide() {
    return Collections.unmodifiableList(xpGuide);
  }

  private XpSource declareXpSource(String key, @Nullable Text guide) {
    for (XpSource existing : xpSources) {
      if (existing.key().equals(key)) {
        throw new IllegalArgumentException(getName() + " declares XP source '" + key + "' twice");
      }
    }
    XpSource source = new XpSource(this, key, guide);
    xpSources.add(source);
    addXpGuideLine(source.guide());
    return source;
  }

  private void addXpGuideLine(Text line) {
    if (!xpGuide.contains(line)) {
      xpGuide.add(line);
    }
  }

  /** Registers a recipe every player can craft while the skill is enabled. */
  public final void registerRecipe(RecipeSpec recipe) {
    recipes.add(Objects.requireNonNull(recipe, "recipe"));
  }

  @Override
  public final List<RecipeSpec> getRecipes() {
    return Collections.unmodifiableList(recipes);
  }

  /**
   * Gives the skill its own blocks, words, upgrades and sounds for mutations. Without it the skill still mutates,
   * with the plain kit Adapt generates and no upgrades. Call once from the constructor.
   *
   * @throws IllegalStateException when a kit was already registered
   */
  @ApiStatus.Experimental
  protected final void registerMutationKit(MutationKit kit) {
    if (mutationKit != null) {
      throw new IllegalStateException(getName() + " registers a mutation kit twice");
    }
    mutationKit = Objects.requireNonNull(kit, "kit");
  }

  @Override
  public final Optional<MutationKit> getMutationKit() {
    return Optional.ofNullable(mutationKit);
  }

  @Override
  public final List<Adaptation<?>> getAdaptations() {
    return runtime.adaptations();
  }

  @Override
  public final List<Adaptation<?>> getUpgrades() {
    return runtime.upgrades();
  }

  @Override
  public final List<MutationBlock<?>> getBlocks() {
    return runtime.blocks();
  }

  /** By default only the {@link CommonPreferences#SKILL_ENABLED} switch; override to offer more. */
  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.SKILL_ENABLED);
  }

  /** The config's {@code enabled} flag. */
  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  /** The colour in effect: the config's {@code skillColor} when it is a valid {@code #RRGGBB}, else the declared one. */
  @Override
  public final Rgb getColor() {
    String configured = getConfig().skillColor;
    if (configured != null && configured.length() == 7 && configured.charAt(0) == '#') {
      try {
        return Rgb.of(Integer.parseInt(configured.substring(1), 16));
      } catch (NumberFormatException invalid) {
        return color;
      }
    }
    return color;
  }

  @Override
  public final Icon getIcon() {
    return icon;
  }

  @Override
  public final double getMinXp() {
    return getConfig().minXp;
  }

  @Override
  public final String getLocalizedName() {
    return runtime.localizedName();
  }

  @Override
  public final String getEmojiName() {
    return runtime.emoji();
  }

  @Override
  public String getDescription() {
    return runtime.description();
  }

  @Override
  public final String getDisplayName() {
    return runtime.displayName(0);
  }

  @Override
  public final String getDisplayName(int level) {
    return runtime.displayName(level);
  }

  @Override
  public final String getShortName() {
    return runtime.shortName();
  }

  @Override
  public final boolean hasUsePermission(PlayerRef player) {
    return runtime.hasUsePermission(player);
  }

  @Override
  public final void xp(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey) {
    runtime.xp(player, at, amount, rewardKey, false, true);
  }

  @Override
  public final void xpS(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey) {
    runtime.xp(player, at, amount, rewardKey, true, true);
  }

  @Override
  public final void xpSilent(PlayerRef player, double amount, @Nullable String rewardKey) {
    runtime.xp(player, null, amount, rewardKey, true, false);
  }

  @Override
  public final void xp(Loc at, double amount, int radius, long durationMillis) {
    runtime.spatialXp(at, amount, radius, durationMillis);
  }

  /**
   * True when a handler should do nothing for this player: the skill is disabled, the player's data is not ready,
   * they lack the use permission, their world is blacklisted, or they are in creative or spectator while the server
   * gives no XP in creative. Also true for null, for a player that is not a real server player (such as an NPC),
   * and on Folia off the thread that owns the player.
   */
  protected final boolean shouldReturnForPlayer(PlayerRef player) {
    return runtime.shouldSkipPlayer(player);
  }

  /**
   * Runs {@code action} on the player's thread unless {@link #shouldReturnForPlayer(PlayerRef)}: at once when the
   * caller owns the player, otherwise (on Folia) on the player's region thread later. Nothing runs once this instance
   * is retired. An exception the action throws is logged at verbose level and not rethrown.
   */
  protected final void shouldReturnForPlayer(PlayerRef player, Runnable action) {
    runtime.withPlayer(player, null, action);
  }

  /** As above, and also does nothing when {@code event} is cancelled. */
  protected final void shouldReturnForPlayer(PlayerRef player, Cancellable event, Runnable action) {
    runtime.withPlayer(player, event, action);
  }

  /**
   * Calls {@code pulse} for each online player whose data has loaded, about once every {@code cadenceMillis} per
   * player, on that player's thread, with the time since that player's last pulse (at most four cadences, so a
   * stalled server does not pay a burst). The cadence is read again before each pulse, and values below 50 count as
   * 50. Call it from the constructor; the pulse stops when the skill unregisters.
   *
   * <p>The first visit to a player only starts their timer, so their first call comes one cadence later. While the
   * skill is disabled nobody is pulsed and the timers restart, and a player who leaves starts over on return. Adapt
   * visits at most 64 players every 50 ms. The pulse runs for any such player, learner of the skill or not, and an
   * exception it throws is logged without stopping later pulses.
   */
  protected final void pulseLearners(LongSupplier cadenceMillis, LearnerPulse pulse) {
    runtime.pulseLearners(Objects.requireNonNull(cadenceMillis, "cadenceMillis"), Objects.requireNonNull(pulse, "pulse"));
  }

  /** True when the skill is disabled, the world is blacklisted, or {@code world} is null. */
  protected final boolean shouldReturnForWorld(WorldRef world) {
    return runtime.shouldSkipWorld(world);
  }

  /** Whether the server's config blacklists the player's world for Adapt; true for null. */
  protected final boolean isWorldBlacklisted(PlayerRef player) {
    return runtime.isWorldBlacklisted(player);
  }

  /** Whether hitting the entity can pay combat XP: a living creature that is not a parrot, vehicle or decoration. */
  public final boolean checkValidEntity(EntityRef entity) {
    if (!(entity instanceof LivingRef) || entity.type().equals(PARROT)) {
      return false;
    }
    Id type = entity.type();
    if (!type.isMinecraft()) {
      return true;
    }
    String name = type.path();
    if (INVALID_DAMAGEABLE_NAMES.contains(name)) {
      return false;
    }
    for (String prefix : INVALID_DAMAGEABLE_PREFIXES) {
      if (name.startsWith(prefix)) {
        return false;
      }
    }
    for (String suffix : INVALID_DAMAGEABLE_SUFFIXES) {
      if (name.endsWith(suffix)) {
        return false;
      }
    }
    return true;
  }

  /**
   * An adaptation a skill declared.
   *
   * @param id      the id the built adaptation reports
   * @param factory builds a fresh adaptation
   */
  public record AdaptationEntry(Id id, Supplier<? extends SimpleAdaptation<?>> factory) {
    public AdaptationEntry {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(factory, "factory");
    }
  }
}
