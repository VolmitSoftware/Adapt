package art.arcane.adapt.platform.event.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Computes the damage that will actually be dealt for a base amount, after armour, enchantments,
 * resistance and absorption. Supplied by the bridge. The Bukkit bridge may write the base amount
 * into the native event to compute it; that is harmless because the bridge writes it back anyway.
 */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0.0")
public interface FinalDamage {
  /** Applies no reductions. */
  FinalDamage IDENTITY = base -> base;

  /** Damage dealt for {@code baseDamage} after every reduction. */
  double of(double baseDamage);
}
