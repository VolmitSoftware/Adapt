package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Attribute modifier operations, in vanilla order. Bukkit names in comments.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AttributeOperation {
  /** ADD_NUMBER. */
  ADD_VALUE,
  /** ADD_SCALAR. */
  ADD_MULTIPLIED_BASE,
  /** MULTIPLY_SCALAR_1. */
  ADD_MULTIPLIED_TOTAL
}
