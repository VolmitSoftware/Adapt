package art.arcane.adapt.platform.event.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Computes how much of a base damage amount a raised shield takes off, as a positive number (Bukkit's negated
 * BLOCKING modifier). Supplied by the bridge; {@link #NONE} when the victim cannot block.
 */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0.0")
public interface BlockedDamage {
  /** Blocks nothing. */
  BlockedDamage NONE = base -> 0.0D;

  /** Amount a raised shield takes off {@code baseDamage}, 0 or more. */
  double of(double baseDamage);
}
