package art.arcane.adapt.api.insight;

import org.jetbrains.annotations.ApiStatus;

/**
 * Ownership of the target: not tameable, tameable and wild, tamed by someone else, or tamed by the viewer.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum InsightBond {
  /** The target cannot be tamed. */
  NONE,
  /** Tameable and untamed. */
  WILD,
  /** Tamed by another player. */
  OWNED,
  /** Tamed by the viewer. */
  YOURS
}
