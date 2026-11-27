package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.event.entity.DamageCause;
import org.jetbrains.annotations.ApiStatus;

/**
 * The last damage an entity took that was not cancelled, like Bukkit's last damage cause. Adapt creates these; new
 * accessors may be added.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface LastDamage {
  /** What dealt the damage. */
  DamageCause cause();

  /**
   * The direct damaging entity (the arrow, not its shooter) when the damage came from an entity, otherwise null.
   */
  EntityRef damager();

  /** The amount after armour, effects, enchantments and absorption. */
  double finalDamage();

  /** True when an entity dealt the damage. */
  default boolean byEntity() {
    return damager() != null;
  }
}
