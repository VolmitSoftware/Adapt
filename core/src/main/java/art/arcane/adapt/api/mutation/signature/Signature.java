package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.blocks.MutatedText;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationEngine;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.sound.SoundPalette;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolOwner;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The hand-made effect of one (lead, partner, recipe), replacing that mutated adaptation's generated blocks. Its row
 * in {@link Signatures} names this class and gives its English name, description and stat labels; the lead skill
 * builds one instance per row, so it listens, ticks and loads config like an adaptation, but no player learns it. Its
 * level for a player is their level in the mutated adaptation {@code mutated-<lead>-<partner>-<recipe>}, which keeps
 * the replaced recipe's price and power slot.
 *
 * <p>A subclass has a public constructor taking the row, which it passes to {@code super}, registers a {@code Config}
 * that extends {@link Config}, and calls {@link #fire} from its {@code @Handle} methods once its action happened. The
 * engine then checks the level, the cooldown and the farm rule, runs {@link #run} and plays the pair's sounds. A
 * Barter or Attunement signature needs no handler: the engine fires it on the pair's gesture or on its interval.
 */
public abstract class Signature<C extends Signature.Config> extends SimpleAdaptation<C> implements ToolOwner {
  public static final String ID_PREFIX = "signature-";

  private final MutationOverride row;
  private final SkillMutation mutation;
  private final List<MutationTool<?>> tools = new ArrayList<>(1);

  protected Signature(MutationOverride row) {
    super(id(row));
    if (!row.isSignature()) {
      throw new IllegalArgumentException("Not a signature row: " + row);
    }
    this.row = row;
    this.mutation = new SkillMutation(row.leadId(), row.partnerId());
    setLocalizationKey(MutatedMessages.signaturePrefix(row));
    setBaseCost(0);
    setInitialCost(0);
  }

  /** The id of a row's signature, such as {@code signature-chronos-swords-convergence}. */
  public static String id(MutationOverride row) {
    return ID_PREFIX + row.leadId() + "-" + row.partnerId() + "-" + row.recipe().name().toLowerCase(Locale.ROOT);
  }

  /** The effect. {@code use.adaptation()} is the mutated adaptation it replaces, {@code use.level()} its level. */
  protected abstract Outcome run(BlockUse use);

  /** Whether the effect gives items, XP, healing, repairs or growth; then it does not run when the action was farmed. */
  public boolean paysOut() {
    return false;
  }

  /** How long after a run it waits; by default the replaced recipe's cooldown at that level. */
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return adaptation.getCooldownMillisForRecipe(level);
  }

  /** Stat lines shown on the mutated adaptation it replaces, labelled with the row's {@code lore} through {@code statLore(v, value, n)}. */
  public void addSignatureStats(int level, StatLines v) {
  }

  /**
   * The action happened: the engine runs the effect for {@code player} if they hold this pair's adaptation, it is
   * ready, and (for an effect that {@linkplain #paysOut pays out}) neither {@code farmed} nor a farmed target.
   * True when it ran.
   */
  protected final boolean fire(PlayerRef player, @Nullable EntityRef target, @Nullable Loc at, boolean farmed) {
    return usable() && MutationEngine.current().signature(this, player, target, at, farmed);
  }

  /** The replaced recipe's levels; a config cannot change them. */
  @Override
  public final int getMaxLevel() {
    return row.recipe().price().maxLevel();
  }

  public MutationOverride getRow() {
    return row;
  }

  public SkillMutation getMutation() {
    return mutation;
  }

  public Recipe getRecipe() {
    return row.recipe();
  }

  /** Whether it may run now: enabled in its config and both parent skills enabled on the server. */
  public boolean usable() {
    if (!isEnabled() || getSkill() == null || !getSkill().isEnabled()) {
      return false;
    }
    Skill<?> partner = getServer().getSkillRegistry().getSkill(row.partnerId());
    return partner != null && partner.isEnabled();
  }

  /** The player's level in the mutated adaptation this replaces; 0 without that mutation or adaptation. */
  public int levelIn(PlayerData data) {
    if (data == null || !data.getMutations().contains(mutation)) {
      return 0;
    }
    PlayerSkillLine line = data.getSkillLineNullable(mutation.id());
    if (line == null) {
      return 0;
    }
    return Math.min(line.getAdaptationLevel(MutationAdaptation.id(mutation, row.recipe())), getMaxLevel());
  }

  public SoundPalette getSoundPalette() {
    return SoundPalettes.of(row.leadId());
  }

  @Override
  protected String localizedName() {
    return AdaptLanguage.render(MutatedText.signatureName(MutatedText.VIEWER, row));
  }

  @Override
  public String getDescription() {
    return AdaptLanguage.render(MutatedText.override(MutatedText.VIEWER, row));
  }

  @Override
  public final void addStats(int level, StatLines v) {
  }

  /** Signatures never pay skill XP, not even through an original's code they reuse. */
  @Override
  public final double payableXp(PlayerRef p, double amount) {
    return 0D;
  }

  /** Registers a tool this signature hands out; its recipes are gated at the tool's required level of this signature. */
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

  /** Runs the effect; only {@link MutationEngine} calls this, after its checks. */
  public final Outcome dispatched(BlockUse use) {
    return run(use);
  }

  /** Every signature's config extends this. */
  public static class Config extends AdaptationConfig {
  }
}
