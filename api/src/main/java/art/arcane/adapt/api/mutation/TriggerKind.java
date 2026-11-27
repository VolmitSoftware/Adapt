package art.arcane.adapt.api.mutation;

import org.jetbrains.annotations.ApiStatus;

/**
 * Whether a skill's Trigger is a combat action, a work action, or can be either. Picks which Strike form runs.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum TriggerKind {
  FIGHT,
  WORK,
  EITHER
}
