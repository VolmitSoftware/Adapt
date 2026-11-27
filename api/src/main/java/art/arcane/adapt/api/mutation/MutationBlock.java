package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * One of the six pieces a skill brings to every mutation it is part of. A block is registered through its skill's
 * {@link MutationKit}; it listens, ticks and loads config like an adaptation, but no player learns it. It runs for
 * a player who holds a mutated adaptation built from it.
 *
 * <p>Its one-line phrase, which generated descriptions are assembled from, is the message
 * {@code <skill key>.block.<slot>}.
 *
 * @param <C> the block's config
 */
@ApiStatus.Experimental
public abstract sealed class MutationBlock<C extends MutationBlock.Config> extends SimpleAdaptation<C>
    permits TriggerBlock, StrikeBlock, BoonBlock, FuelBlock, StateBlock {
  private final BlockKey key;

  MutationBlock(Id skill, BlockSlot slot) {
    super(new BlockKey(skill, slot).id());
    this.key = new BlockKey(skill, slot);
    setLocalizationKey(TextKeys.block(skill, slot));
  }

  /** The skill and slot this block fills. */
  public final BlockKey getKey() {
    return key;
  }

  /** The slot this block fills. */
  public final BlockSlot getSlot() {
    return key.slot();
  }

  /** The kit of the skill this block belongs to: the one it registered, or the one Adapt generates for it. */
  public final MutationKit getKit() {
    return Engine.bridge().mutations().kit(getSkill());
  }

  /** The sounds of the skill this block belongs to. */
  public final SoundPalette getSoundPalette() {
    return getKit().sounds();
  }

  /** Whether this block may run now: enabled in its config and its skill enabled on the server. */
  public final boolean usable() {
    return isEnabled() && getSkill().isEnabled();
  }

  @Override
  public final int getMaxLevel() {
    return 1;
  }

  @Override
  public final void addStats(int level, StatLines lines) {
  }

  /** Blocks never pay skill XP, not even through an original's code they reuse. */
  @Override
  public final double payableXp(PlayerRef player, double amount) {
    return 0D;
  }

  /** Every block's config extends this. */
  public static class Config extends AdaptationConfig {
    public Config() {
      maxLevel = 1;
      baseCost = 0;
      initialCost = 0;
    }
  }
}
