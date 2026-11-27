package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One run of a block or signature for a mutated adaptation.
 *
 * @param player     the player the run is for
 * @param adaptation the mutated adaptation that runs; its level and preferences are the player's
 * @param slot       the pair and recipe of that adaptation
 * @param level      the player's level in it
 * @param target     set for a fight Strike
 * @param at         set for a work Strike
 * @param initiator  the skill whose Trigger, Fuel or State started the run, so a block can act differently when
 *                   another skill set it off
 * @param payer      the adaptation a Fuel charges its cost through: the mutated adaptation, or the upgrade that
 *                   spends the Fuel as an overcharge
 */
@ApiStatus.Experimental
public record BlockUse(PlayerRef player, Adaptation<?> adaptation, MutationSlot slot, int level,
                       @Nullable LivingRef target, @Nullable Loc at, Id initiator, Adaptation<?> payer) {
  public BlockUse {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(adaptation, "adaptation");
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(initiator, "initiator");
    Objects.requireNonNull(payer, "payer");
  }

  /** 0 at level 1, 1 at the adaptation's top level, in a line between; always 1 for a single-level adaptation. */
  public double power() {
    int maxLevel = adaptation.getMaxLevel();
    return maxLevel <= 1 ? 1D : Math.min(1D, Math.max(0D, (level - 1D) / (maxLevel - 1D)));
  }

  /** {@code atFirstLevel} at level 1, rising in a line to {@code atTopLevel} at the top level. */
  public double scaled(double atFirstLevel, double atTopLevel) {
    return atFirstLevel + ((atTopLevel - atFirstLevel) * power());
  }

  /** The two skills of the mutation; {@code slot().pair()}. */
  public SkillPair pair() {
    return slot.pair();
  }

  /** Which of the five adaptations runs; {@code slot().recipe()}. */
  public Recipe recipe() {
    return slot.recipe();
  }

  /** Where the effect is centred: the work location, else the target, else the player. */
  public Loc center() {
    if (at != null) {
      return at;
    }
    return target != null ? target.location() : player.location();
  }
}
