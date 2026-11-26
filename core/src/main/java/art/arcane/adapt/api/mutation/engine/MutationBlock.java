package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.blocks.BlockRef;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.MutatedText;
import art.arcane.adapt.api.mutation.sound.SoundPalette;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * One of a skill's six mutation blocks. It is registered by its skill with
 * {@link art.arcane.adapt.api.skill.SimpleSkill#registerBlocks}, so it listens, ticks and loads its config like an
 * adaptation, but no player learns it. Its level for a player is the highest level of the mutated adaptations that
 * player holds which use it, so the usual {@code getActiveLevel}, {@code resolveMeleeContext} and protection helpers
 * work inside a block and keep it inert for anyone without such an adaptation.
 *
 * <p>Subclasses call {@code super("block-<skill>-<slot>")} with a literal id and register a {@code Config} that
 * extends {@link Config}.
 */
public abstract class MutationBlock<C extends MutationBlock.Config> extends SimpleAdaptation<C> {
  private final BlockRef ref;

  protected MutationBlock(String id, BlockSlot... allowed) {
    super(id);
    ref = MutationBlocks.parse(id);
    if (ref == null) {
      throw new IllegalArgumentException("Not a mutation block id: " + id);
    }
    boolean allowedSlot = false;
    for (BlockSlot slot : allowed) {
      allowedSlot |= slot == ref.slot();
    }
    if (!allowedSlot) {
      throw new IllegalArgumentException(id + " is a " + ref.slot() + " block, not a " + getClass().getSimpleName());
    }
    setLocalizationKey(MutationBlocks.localizationKey(ref));
    setMaxLevel(1);
    setBaseCost(0);
    setInitialCost(0);
  }

  @Override
  protected String localizedName() {
    return AdaptLanguage.render(MutatedText.blockName(MutatedText.VIEWER, ref));
  }

  public BlockRef getRef() {
    return ref;
  }

  public BlockSlot getSlot() {
    return ref.slot();
  }

  public String getSkillId() {
    return ref.skillId();
  }

  public SoundPalette getSoundPalette() {
    return SoundPalettes.of(ref.skillId());
  }

  /** Whether this block may run now: enabled in its config and its skill enabled on the server. */
  public boolean usable() {
    return isEnabled() && getSkill() != null && getSkill().isEnabled();
  }

  @Override
  public final void addStats(int level, StatLines v) {
  }

  /** Blocks never pay skill XP, not even through an original's code they reuse. */
  @Override
  public final double payableXp(PlayerRef p, double amount) {
    return 0D;
  }

  /** Every block's config extends this. */
  public static class Config extends AdaptationConfig {
  }
}
