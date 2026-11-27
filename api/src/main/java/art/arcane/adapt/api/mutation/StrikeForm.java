package art.arcane.adapt.api.mutation;

import org.jetbrains.annotations.ApiStatus;

/**
 * Which Strike a mutated adaptation runs.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum StrikeForm {
  /** The fight Strike, at an enemy. */
  FIGHT,
  /** The work Strike, at the blocks, crops or drops around the player. */
  WORK,
  /** The fight Strike when the action has a living target, else the work Strike. */
  EITHER,
  /** No Trigger sets the adaptation off: Barter and Attunement. */
  NONE;

  /** The form a Trigger of {@code kind} sets off: {@code FIGHT}, {@code WORK} or {@code EITHER}, never {@code NONE}. */
  public static StrikeForm of(TriggerKind kind) {
    return switch (kind) {
      case FIGHT -> FIGHT;
      case WORK -> WORK;
      case EITHER -> EITHER;
    };
  }
}
