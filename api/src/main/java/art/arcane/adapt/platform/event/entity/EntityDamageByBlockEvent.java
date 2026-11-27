package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * Damage the platform attributes to a block (Bukkit EntityDamageByBlockEvent): fire, campfires, magma, cactus,
 * berry bushes, dripstone and block explosions. {@link #damager()} is that block, or null when the platform knows
 * the damage came from a block but not which one.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityDamageByBlockEvent extends EntityDamageEvent {
  private final BlockRef damager;

  public EntityDamageByBlockEvent(EntityRef entity, BlockRef damager, DamageCause cause, Id damageType, double damage,
                                  FinalDamage finalDamage, BlockedDamage blockedDamage) {
    super(entity, cause, damageType, damage, finalDamage, blockedDamage);
    this.damager = damager;
  }

  /** The block that dealt the damage, or null when the platform does not know which one. */
  public BlockRef damager() {
    return damager;
  }
}
