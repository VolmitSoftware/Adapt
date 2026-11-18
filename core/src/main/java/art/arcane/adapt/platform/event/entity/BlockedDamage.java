package art.arcane.adapt.platform.event.entity;

/**
 * Computes how much of a base damage amount a raised shield takes off, as a positive number (Bukkit's negated
 * BLOCKING modifier). Supplied by the bridge; {@link #NONE} when the victim cannot block.
 */
@FunctionalInterface
public interface BlockedDamage {
  BlockedDamage NONE = base -> 0.0D;

  double of(double baseDamage);
}
