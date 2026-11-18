package art.arcane.adapt.platform.event.entity;

/**
 * Computes the damage that will actually be dealt for a base amount, after armour, enchantments,
 * resistance and absorption. Supplied by the bridge. The Bukkit bridge may write the base amount
 * into the native event to compute it; that is harmless because the bridge writes it back anyway.
 */
@FunctionalInterface
public interface FinalDamage {
  FinalDamage IDENTITY = base -> base;

  double of(double baseDamage);
}
