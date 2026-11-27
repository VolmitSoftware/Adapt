package art.arcane.adapt.api.mutation;

import org.jetbrains.annotations.ApiStatus;

/**
 * The moment a mutated adaptation fires in, which picks the cue each parent skill contributes.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum MutationCue {
  FIGHT,
  WORK,
  BOON
}
