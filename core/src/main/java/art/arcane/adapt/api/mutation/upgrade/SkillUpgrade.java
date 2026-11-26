package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.MutationPrice;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlocks;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.sound.SoundPalette;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.mutation.sound.SoundStage;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolOwner;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.adapt.localization.catalog.UpgradeMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One of a skill's three upgraded adaptations. It is registered by its skill with
 * {@link art.arcane.adapt.api.skill.SimpleSkill#registerUpgrades}, so it ticks, listens and loads config like any
 * adaptation, but its level is 0 for every player who has not mutated that skill. Its name and description come from
 * {@link art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog}, its price is the catalog's
 * ({@link MutationPrice#UPGRADED} or {@link MutationPrice#TOOL}), and it plays its skill's sound palette.
 *
 * <p>Subclasses call {@code super("upgraded-<skill>-<slug>")} with a literal id, register a {@code Config} that
 * extends {@link Config} (or {@link ToolConfig} for a tool upgrade), and reuse their originals' logic through helpers
 * the originals also call. An upgrade may declare an {@linkplain #declareOvercharge() overcharge} and register
 * {@linkplain #registerTool tools}.
 */
public abstract class SkillUpgrade<C extends SkillUpgrade.Config> extends SimpleAdaptation<C> implements ToolOwner {
  /** Per-upgrade switch for the overcharge, offered only by upgrades that declare one. */
  public static final PlayerPreference<CommonPreferences.Toggle> OVERCHARGE =
      CommonPreferences.toggle("overcharge", UpgradeMessages.OVERCHARGE);

  private final UpgradeSpec spec;
  private final List<MutationTool<?>> tools = new ArrayList<>(1);
  private boolean overcharge;
  private final Set<String> sourceNames;
  private final Map<String, Boolean> fused = new ConcurrentHashMap<>();
  private final Map<UUID, UpgradeXp> xpAllowances = playerState();

  protected SkillUpgrade(String id) {
    super(id);
    spec = SkillUpgrades.require(id);
    setLocalizationKey(spec.localizationKey());
    Set<String> names = new HashSet<>();
    for (String source : spec.upgrade().sources()) {
      names.add(source.toLowerCase(Locale.ROOT));
    }
    sourceNames = Set.copyOf(names);
  }

  public UpgradeSpec getUpgradeSpec() {
    return spec;
  }

  /**
   * The upgrade's own preferences; the overcharge switch is appended when it declares one. Override this instead of
   * {@link #getPlayerPreferences()}.
   */
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED);
  }

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
   * Declares that this upgrade has an overcharge (GEN-2): when its effect fires it may spend the partner skill's Fuel
   * for a stronger version, through {@link #overcharge}. Call it in the constructor; the upgrade then needs the lore
   * key {@code upgrade.<skill>.<slug>.overcharge} naming what grows, shown as a stat line.
   */
  protected final void declareOvercharge() {
    overcharge = true;
  }

  public boolean hasOvercharge() {
    return overcharge;
  }

  /** The key of the overcharge stat label, {@code upgrade.<skill>.<slug>.overcharge}. */
  public String overchargeKey() {
    return spec.localizationKey() + ".overcharge";
  }

  /**
   * Called once when the effect fires. When this upgrade declares an overcharge, the player has it switched on and the
   * partner skill of their mutation has a Fuel they can pay, the Fuel is paid and this returns the overcharge
   * strength ({@code [mutations] overchargeStrength}); otherwise it returns 1 and nothing is paid. Multiply what the
   * effect grants by the result.
   */
  protected final double overcharge(PlayerRef player) {
    if (!overcharge || player == null || !preferenceEnabled(player, OVERCHARGE)) {
      return 1D;
    }
    FuelBlock<?> fuel = partnerFuel(player);
    MutationAdaptation carrier = fuel == null ? null : carrier(player);
    if (carrier == null) {
      return 1D;
    }
    BlockUse use = new BlockUse(player, carrier, Math.max(1, getLevel(player)), null, player.location(),
        getSkill().getName(), this);
    if (!fuel.canPay(use) || !fuel.pay(use)) {
      return 1D;
    }
    return AdaptConfig.get().getMutations().getOverchargeStrength();
  }

  /** The Fuel of the other skill in the player's mutation of this upgrade's skill, or null. */
  public @Nullable FuelBlock<?> partnerFuel(PlayerRef player) {
    SkillMutation mutation = mutation(player);
    if (mutation == null) {
      return null;
    }
    String partner = mutation.lead().equals(getSkill().getName()) ? mutation.partner() : mutation.lead();
    Skill<?> partnerSkill = getServer().getSkillRegistry().getSkill(partner);
    MutationBlock<?> fuel = MutationBlocks.find(partnerSkill, BlockSlot.FUEL);
    return fuel instanceof FuelBlock<?> payable && payable.usable() ? payable : null;
  }

  private @Nullable MutationAdaptation carrier(PlayerRef player) {
    SkillMutation mutation = mutation(player);
    MutatedSkill mutated = mutation == null ? null : getServer().getSkillRegistry().getMutatedSkill(mutation.id());
    List<MutationAdaptation> own = mutated == null ? List.of() : mutated.getMutationAdaptations();
    return own.isEmpty() ? null : own.getFirst();
  }

  private @Nullable SkillMutation mutation(PlayerRef player) {
    AdaptPlayer adaptPlayer = getPlayer(player);
    return adaptPlayer == null || getSkill() == null ? null : adaptPlayer.getData().mutationOf(getSkill().getName());
  }

  @Override
  public void addLevelStats(int level, StatLines v) {
    addStats(level, v);
    if (overcharge) {
      double bonus = AdaptConfig.get().getMutations().getOverchargeStrength() - 1D;
      art.arcane.adapt.util.common.format.C color = art.arcane.adapt.util.common.format.C.LIGHT_PURPLE;
      statLore(v, color, "+ ", Form.pc(bonus, 0), color, AdaptMessages.require(overchargeKey()));
    }
  }

  /**
   * Registers a tool this upgrade hands out. Its recipes become this upgrade's recipes, gated at the tool's required
   * level, so nobody without the upgrade can craft it.
   */
  protected final <T extends MutationTool<?>> T registerTool(T tool) {
    tools.add(tool);
    for (RecipeSpec recipe : tool.recipes()) {
      registerRecipe(recipe.withRequiredLevel(Math.max(recipe.requiredLevel(), tool.requiredLevel())));
    }
    return tool;
  }

  @Override
  public List<MutationTool<?>> getTools() {
    return List.copyOf(tools);
  }

  /** Display names of the originals this upgrade fuses. */
  public List<String> getSourceNames() {
    return spec.upgrade().sources();
  }

  /** Whether this upgrade fuses {@code original}, one of its skill's own adaptations named in its catalog sources. */
  public boolean fuses(Adaptation<?> original) {
    if (original == null || original == this || original instanceof SkillUpgrade<?> || original.getSkill() != getSkill()) {
      return false;
    }
    return fused.computeIfAbsent(original.getName(), (String name) -> {
      String english = SkillUpgrades.englishName(original);
      return english != null && sourceNames.contains(english.toLowerCase(Locale.ROOT));
    });
  }

  /** The level this upgrade at {@code level} lends {@code original}, one of the originals it fuses. */
  public int lend(Adaptation<?> original, int level) {
    return UpgradeLoans.lent(original, this, level);
  }

  /** Called when an original this upgrade fuses lands an effect on a level it lent; plays the work cue. */
  public void landed(PlayerRef player) {
    cue(player, MutationCue.WORK);
  }

  /** Multiplier on what {@code original} grants on a level this upgrade lent at {@code upgradeLevel}. */
  public double strength(Adaptation<?> original, int upgradeLevel) {
    return 1D;
  }

  /** One stat line per fused original this upgrade runs, naming the level it works at with this upgrade at {@code level}. */
  protected void grantedLore(StatLines v, int level) {
    if (getSkill() == null) {
      return;
    }
    for (Adaptation<?> original : getSkill().getAdaptations()) {
      if (fuses(original) && runsOnGrant(original)) {
        v.addLore(SkillUpgrades.grantedLine(original, lend(original, level)));
      }
    }
  }

  /** Whether {@code original}, which this upgrade fuses, runs its own code at the granted level for the holder. */
  protected boolean runsOnGrant(Adaptation<?> original) {
    return true;
  }

  /** Every XP an upgrade pays, through these helpers or through an original's code, is held to the per-minute cap. */
  @Override
  public final double payableXp(PlayerRef p, double amount) {
    return xpAllowances.computeIfAbsent(p.uuid(), (UUID ignored) -> new UpgradeXp()).claim(amount, System.currentTimeMillis());
  }

  /** Pays {@code amount} for acting on {@code mob} when it is a natural mob or another player. */
  protected final void xpOn(PlayerRef p, EntityRef mob, double amount) {
    if (mob != null && UpgradeXp.naturalMob(p, mob)) {
      xp(p, amount);
    }
  }

  /** {@link #xpOn(PlayerRef, EntityRef, double)} without the XP notice, for effects that land on every hit. */
  protected final void xpSilentOn(PlayerRef p, EntityRef mob, double amount) {
    if (mob != null && UpgradeXp.naturalMob(p, mob)) {
      xpSilent(p, amount);
    }
  }

  /** Pays {@code amount} for acting on {@code block} when no player placed or bone-mealed it. */
  protected final void xpOn(PlayerRef p, BlockRef block, double amount) {
    if (UpgradeXp.naturalBlock(block)) {
      xp(p, amount);
    }
  }

  /** Pays {@code perCrop} for every crop among {@code crops} that was not grown with bone meal. */
  protected final void xpOnHarvests(PlayerRef p, Collection<BlockRef> crops, double perCrop) {
    int natural = 0;
    for (BlockRef crop : crops) {
      if (UpgradeXp.naturalHarvest(crop)) {
        natural++;
      }
    }
    if (natural > 0) {
      xp(p, natural * perCrop);
    }
  }

  /** Pays {@code amount} for answering a hit when {@code damager}, or its shooter, is a natural mob or a player. */
  protected final void xpFromAttacker(PlayerRef p, EntityRef damager, double amount) {
    if (damager != null && UpgradeXp.realAttacker(p, damager)) {
      xp(p, amount);
    }
  }

  /** Pays {@code amount} for a discovery the player can make only once, such as a first visit or a first relic. */
  protected final void xpOnDiscovery(PlayerRef p, double amount) {
    xp(p, amount);
  }

  /** Pays {@code perMob} for every natural mob or player among {@code mobs}. */
  protected final void xpOnMobs(PlayerRef p, Collection<? extends EntityRef> mobs, double perMob) {
    int natural = 0;
    for (EntityRef mob : mobs) {
      if (UpgradeXp.naturalMob(p, mob)) {
        natural++;
      }
    }
    if (natural > 0) {
      xp(p, natural * perMob);
    }
  }

  /** Pays {@code perBlock} for every natural block among {@code blocks}. */
  protected final void xpOnBlocks(PlayerRef p, Collection<BlockRef> blocks, double perBlock) {
    int natural = 0;
    for (BlockRef block : blocks) {
      if (UpgradeXp.naturalBlock(block)) {
        natural++;
      }
    }
    if (natural > 0) {
      xp(p, natural * perBlock);
    }
  }

  public SoundPalette getSoundPalette() {
    return SoundPalettes.of(spec.skillId());
  }

  /**
   * Plays this skill's cue for a one-shot effect at the player. Returns false when the 150 ms cooldown swallowed it.
   */
  protected boolean cue(PlayerRef player, MutationCue cue) {
    return SkillUpgrades.SOUNDS.cue(player.uuid(), getName(), getSoundPalette(), cue, SoundStage.following(this, player));
  }

  /**
   * Starts the quiet tick for an effect that lasts, following {@code entity}. Stop it when the effect ends; do not use
   * it for instant effects.
   */
  protected LingeringSound linger(EntityRef entity) {
    return SkillUpgrades.SOUNDS.linger(getSoundPalette(), SoundStage.following(this, entity));
  }

  /** Every upgrade's config extends this, which prices it at the upgraded tier: 13, 13 and 16 knowledge. */
  public static class Config extends AdaptationConfig {
    public Config() {
      this(MutationPrice.UPGRADED);
    }

    protected Config(MutationPrice price) {
      baseCost = price.baseCost();
      initialCost = price.initialCost();
      costFactor = price.costFactor();
      maxLevel = price.maxLevel();
    }
  }

  /**
   * The config of an upgrade the catalog prices at the tool tier (ECO-1): 15, 14 and 18 knowledge. A test fails when an
   * upgrade's config and its catalog price disagree.
   */
  public static class ToolConfig extends Config {
    public ToolConfig() {
      super(MutationPrice.TOOL);
    }
  }
}
