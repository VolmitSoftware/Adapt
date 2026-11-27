package art.arcane.adapt.api.insight;

import org.jetbrains.annotations.ApiStatus;

/**
 * What Insight reveals the target to be; the client tints the plate by it.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum InsightKind {
  /** Another player. */
  PLAYER,
  /** A hostile creature. */
  HOSTILE,
  /** Any other creature. */
  PASSIVE
}
