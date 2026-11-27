package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * An upgraded adaptation: a stronger ability that stands in for its sources once its skill is mutated, and keeps the
 * code of the sources it fuses running at a level it lends.
 * Declared in the skill's {@link MutationKit}. It ticks, listens and loads config like any adaptation, but its
 * level is 0 for every player who has not mutated that skill.
 *
 * <p>A subclass registers a config that extends {@link Config}, or {@link ToolConfig} for a tool upgrade, and
 * reuses its originals' logic through helpers the originals also call. It may declare an
 * {@linkplain #declareOvercharge() overcharge} and register {@linkplain #registerTool tools}.
 *
 * @param <C> the upgrade's config
 */
@ApiStatus.Experimental
public abstract class SkillUpgrade<C extends SkillUpgrade.Config> extends SimpleAdaptation<C> {
  /** Per-upgrade switch for the overcharge, offered only by upgrades that declare one. */
  public static final PlayerPreference<CommonPreferences.Toggle> OVERCHARGE =
      CommonPreferences.toggle("overcharge", Text.key("adapt.upgrade.overcharge"));

  private final List<MutationTool<?>> tools = new ArrayList<>(1);
  private boolean overcharge;

  /**
   * @param id the id the upgrade registers under; must match the id its kit declares
   */
  protected SkillUpgrade(Id id) {
    super(id);
  }

  /** The upgrade's own preferences. Override this instead of {@link #getPlayerPreferences()}. */
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED);
  }

  /** The upgrade's preferences, with the overcharge switch appended when it declares one. */
  @Override
  public final List<PlayerPreference<?>> getPlayerPreferences() {
    List<PlayerPreference<?>> own = upgradePreferences();
    if (!overcharge) {
      return own;
    }
    List<PlayerPreference<?>> all = new ArrayList<>(own);
    all.add(OVERCHARGE);
    return List.copyOf(all);
  }

  /**
   * Declares that this upgrade has an overcharge: when its effect fires it may spend the partner skill's Fuel for a
   * stronger version, through {@link #overcharge}. Call it in the constructor. The upgrade then needs the message
   * {@code <upgrade key>.overcharge} naming what grows, which is shown as a stat line.
   */
  protected final void declareOvercharge() {
    overcharge = true;
  }

  /** Whether the constructor called {@link #declareOvercharge()}. */
  public final boolean hasOvercharge() {
    return overcharge;
  }

  /**
   * Called once when the effect fires. When this upgrade declares an overcharge, the player has it switched on and
   * the partner skill of their mutation has a Fuel they can pay, the Fuel is paid and this returns the server's
   * overcharge strength; otherwise it returns 1 and nothing is paid. Multiply what the effect grants by the result.
   */
  protected final double overcharge(PlayerRef player) {
    if (!overcharge || !preferenceEnabled(player, OVERCHARGE)) {
      return 1D;
    }
    return Engine.bridge().mutations().overcharge(this, player);
  }

  /**
   * Registers a tool this upgrade hands out. Its recipes become this upgrade's recipes, gated at the tool's
   * required level, so nobody without the upgrade can craft it.
   */
  protected final <T extends MutationTool<?>> T registerTool(T tool) {
    tools.add(tool);
    registerItem(tool.item());
    for (RecipeSpec recipe : tool.recipes()) {
      registerRecipe(recipe.withRequiredLevel(Math.max(recipe.requiredLevel(), tool.requiredLevel())));
    }
    return tool;
  }

  /** The tools this upgrade registered, in registration order. */
  public final List<MutationTool<?>> getTools() {
    return List.copyOf(tools);
  }

  /** The originals this upgrade stands in for, as its kit declares them in {@link UpgradeDeclaration#sources()}. */
  public final List<Id> getSources() {
    return Engine.bridge().mutations().sources(this);
  }

  /** The sources whose own code keeps running at the level this upgrade lends; {@link UpgradeDeclaration#fuses()}. */
  public final Set<Id> getFuses() {
    return Engine.bridge().mutations().fuses(this);
  }

  /** Whether {@code original} is one of this upgrade's sources. */
  public final boolean isSource(Adaptation<?> original) {
    return original != this && getSources().contains(original.getId());
  }

  /** Whether this upgrade fuses {@code original}, so the original's code runs at the level this upgrade lends. */
  public final boolean fuses(Adaptation<?> original) {
    return original != this && getFuses().contains(original.getId());
  }

  /**
   * The level this upgrade at {@code level} lends {@code original}. By default the original's top level at the
   * upgrade's top level, one less for each level the upgrade is short of its top, and never below 1.
   */
  public int lend(Adaptation<?> original, int level) {
    if (level <= 0 || !isSource(original)) {
      return 0;
    }
    return Math.max(1, original.getMaxLevel() - Math.max(0, getMaxLevel() - level));
  }

  /** An original this upgrade fuses landed an effect for the player on a level this upgrade lent; plays the work cue. */
  public void landed(PlayerRef player) {
    cue(player, MutationCue.WORK);
  }

  /** The multiplier this upgrade at {@code upgradeLevel} puts on what {@code original} grants. */
  public double strength(Adaptation<?> original, int upgradeLevel) {
    return 1D;
  }

  /** The sounds of the skill this upgrade belongs to. */
  public final SoundPalette getSoundPalette() {
    return Engine.bridge().mutations().kit(getSkill()).sounds();
  }

  /** The upgrade's stats, then the overcharge line when it declares one. */
  @Override
  public void addLevelStats(int level, StatLines lines) {
    addStats(level, lines);
    if (overcharge) {
      double bonus = Engine.bridge().mutations().overchargeStrength() - 1D;
      statLore(lines, TextFormat.LIGHT_PURPLE, "+ ", Math.round(bonus * 100D) + "%", TextFormat.LIGHT_PURPLE,
          Text.key(getLocalizationKey() + ".overcharge"));
    }
  }

  /** One stat line per original this upgrade fuses, naming the level it works at with this upgrade at {@code level}. */
  protected final void grantedLore(StatLines lines, int level) {
    for (Adaptation<?> original : getSkill().getAdaptations()) {
      if (fuses(original)) {
        lines.addLore(Engine.bridge().mutations().grantedLine(original, lend(original, level)));
      }
    }
  }

  /** Every XP an upgrade pays, through these helpers or through an original's code, is held to the per-minute cap. */
  @Override
  public final double payableXp(PlayerRef player, double amount) {
    return Engine.bridge().mutations().claimXp(this, player, amount);
  }

  /** Pays {@code amount} for acting on {@code mob} when it is a natural mob or another player. */
  protected final void xpOn(PlayerRef player, @Nullable EntityRef mob, double amount) {
    if (mob != null && Engine.bridge().mutations().naturalMob(player, mob)) {
      xp(player, amount);
    }
  }

  /** {@link #xpOn(PlayerRef, EntityRef, double)} without the XP notice, for effects that land on every hit. */
  protected final void xpSilentOn(PlayerRef player, @Nullable EntityRef mob, double amount) {
    if (mob != null && Engine.bridge().mutations().naturalMob(player, mob)) {
      xpSilent(player, amount);
    }
  }

  /** Pays {@code amount} for acting on {@code block} when no player placed or bone-mealed it. */
  protected final void xpOn(PlayerRef player, BlockRef block, double amount) {
    if (!Mutations.farmed(block)) {
      xp(player, amount);
    }
  }

  /** Pays {@code perCrop} for every crop among {@code crops} that was not grown with bone meal. */
  protected final void xpOnHarvests(PlayerRef player, Collection<BlockRef> crops, double perCrop) {
    int natural = 0;
    for (BlockRef crop : crops) {
      if (!Mutations.farmedHarvest(crop)) {
        natural++;
      }
    }
    if (natural > 0) {
      xp(player, natural * perCrop);
    }
  }

  /** Pays {@code amount} for answering a hit when {@code damager}, or its shooter, is a natural mob or a player. */
  protected final void xpFromAttacker(PlayerRef player, @Nullable EntityRef damager, double amount) {
    if (damager != null && Engine.bridge().mutations().realAttacker(player, damager)) {
      xp(player, amount);
    }
  }

  /** Pays {@code amount} for a discovery the player can make only once, such as a first visit or a first relic. */
  protected final void xpOnDiscovery(PlayerRef player, double amount) {
    xp(player, amount);
  }

  /** Pays {@code perMob} for every natural mob or player among {@code mobs}. */
  protected final void xpOnMobs(PlayerRef player, Collection<? extends EntityRef> mobs, double perMob) {
    int natural = 0;
    for (EntityRef mob : mobs) {
      if (Engine.bridge().mutations().naturalMob(player, mob)) {
        natural++;
      }
    }
    if (natural > 0) {
      xp(player, natural * perMob);
    }
  }

  /** Pays {@code perBlock} for every natural block among {@code blocks}. */
  protected final void xpOnBlocks(PlayerRef player, Collection<BlockRef> blocks, double perBlock) {
    int natural = 0;
    for (BlockRef block : blocks) {
      if (!Mutations.farmed(block)) {
        natural++;
      }
    }
    if (natural > 0) {
      xp(player, natural * perBlock);
    }
  }

  /** Plays this skill's cue for a one-shot effect at the player; false when the 150 ms cooldown swallowed it. */
  protected final boolean cue(PlayerRef player, MutationCue cue) {
    return Engine.bridge().mutations().cue(this, getSoundPalette(), player, cue);
  }

  /** Starts the quiet tick of an effect that lasts, following {@code entity}; stop it when the effect ends. */
  protected final LingeringSound linger(EntityRef entity) {
    return Engine.bridge().mutations().linger(this, getSoundPalette(), entity);
  }

  /** Every upgrade's config extends this. */
  public static class Config extends AdaptationConfig {
    /** Prices the upgrade at {@link MutationPrice#UPGRADED}. */
    public Config() {
      this(MutationPrice.UPGRADED);
    }

    /** Prices the upgrade at {@code price}: its costs and level count become the config's defaults. */
    protected Config(MutationPrice price) {
      baseCost = price.baseCost();
      initialCost = price.initialCost();
      costFactor = price.costFactor();
      maxLevel = price.maxLevel();
    }
  }

  /** The config of an upgrade that hands out a tool. */
  public static class ToolConfig extends Config {
    /** Prices the upgrade at {@link MutationPrice#TOOL}. */
    public ToolConfig() {
      super(MutationPrice.TOOL);
    }
  }
}
