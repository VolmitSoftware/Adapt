package art.arcane.adapt.api.player;

import art.arcane.adapt.api.mutation.SkillPair;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/** The outcome of a mutate or unmutate. Adapt creates results; new accessors may be added. */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface MutationResult {
  /** How the call ended. */
  Status status();

  /** The pair, set when the status is a success. */
  Optional<SkillPair> pair();

  /** Whether the status is {@link Status#MUTATED} or {@link Status#UNMUTATED}. */
  default boolean succeeded() {
    return status() == Status.MUTATED || status() == Status.UNMUTATED;
  }

  /**
   * How a mutate or unmutate ended.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  enum Status {
    /** The two skills were merged. */
    MUTATED,
    /** The mutation was split back into its skills. */
    UNMUTATED,
    /** A skill id names no registered skill. */
    UNKNOWN_SKILL,
    /** A skill is disabled, vetoed, or has no mutation kit. */
    UNAVAILABLE_SKILL,
    /** The lead and the partner are the same skill. */
    SAME_SKILL,
    /** One of the skills is already part of a mutation. */
    ALREADY_MUTATED,
    /** Unmutate was asked for a skill that is not part of a mutation. */
    NOT_MUTATED,
    /** One of the skills is below the level the server requires for a mutation. */
    LEVEL_TOO_LOW,
    /** The player already holds as many mutations as the server allows. */
    LIMIT_REACHED,
    /** A listener cancelled the event. */
    CANCELLED
  }
}
