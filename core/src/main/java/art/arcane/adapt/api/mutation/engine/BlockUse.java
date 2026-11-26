package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.Nullable;

/**
 * One run of a block or signature for a mutated adaptation. {@code target} is set for a fight Strike, {@code at} for a
 * work Strike; {@code initiator} is the skill whose Trigger, Fuel or State started the run, which lets a block act
 * differently when another skill set it off. {@code payer} is the adaptation a Fuel charges its cost through: the
 * mutated adaptation itself, or the upgrade that spends the Fuel as an overcharge. Effects scale with {@link #scaled}.
 */
public record BlockUse(PlayerRef player, MutationAdaptation adaptation, int level, @Nullable LivingRef target,
                       @Nullable Loc at, String initiator, Adaptation<?> payer) {
  public BlockUse(PlayerRef player, MutationAdaptation adaptation, int level, @Nullable LivingRef target,
                  @Nullable Loc at, String initiator) {
    this(player, adaptation, level, target, at, initiator, adaptation);
  }

  /** 0 at level 1, 1 at the adaptation's top level. */
  public double power() {
    int maxLevel = adaptation.getMaxLevel();
    return maxLevel <= 1 ? 1D : Math.min(1D, Math.max(0D, (level - 1D) / (maxLevel - 1D)));
  }

  /** {@code atFirstLevel} at level 1, rising in a line to {@code atTopLevel} at the top level. */
  public double scaled(double atFirstLevel, double atTopLevel) {
    return atFirstLevel + ((atTopLevel - atFirstLevel) * power());
  }

  public SkillMutation mutation() {
    return adaptation.getMutation();
  }

  public Recipe recipe() {
    return adaptation.getRecipe();
  }

  /** Where the effect is centred: the work location, else the target, else the player. */
  public Loc center() {
    if (at != null) {
      return at;
    }
    return target != null ? target.location() : player.location();
  }
}
