package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Damage dealt by an entity. {@link #damager()} is the direct source (the arrow for a bow shot);
 * {@link #causingEntity()} is the responsible entity (the shooter), equal to the damager for melee. Mod loaders post it
 * for every hit whose source names a direct or causing entity.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityDamageByEntityEvent extends EntityDamageEvent {
  private final EntityRef damager;
  private final EntityRef causingEntity;
  private final boolean critical;

  public EntityDamageByEntityEvent(EntityRef entity, EntityRef damager, EntityRef causingEntity, DamageCause cause,
                                   Id damageType, double damage, boolean critical, FinalDamage finalDamage) {
    this(entity, damager, causingEntity, cause, damageType, damage, critical, finalDamage, BlockedDamage.NONE);
  }

  public EntityDamageByEntityEvent(EntityRef entity, EntityRef damager, EntityRef causingEntity, DamageCause cause,
                                   Id damageType, double damage, boolean critical, FinalDamage finalDamage,
                                   BlockedDamage blockedDamage) {
    super(entity, cause, damageType, damage, finalDamage, blockedDamage);
    this.damager = Objects.requireNonNull(damager, "damager");
    this.causingEntity = causingEntity == null ? damager : causingEntity;
    this.critical = critical;
  }

  /** The entity that hit directly: the arrow for a bow shot, the attacker for melee. */
  public EntityRef damager() {
    return damager;
  }

  /** The entity responsible for the hit, such as an arrow's shooter; the damager when there is no separate one. */
  public EntityRef causingEntity() {
    return causingEntity;
  }

  /** True for a player's critical melee hit or a critical arrow. */
  public boolean isCritical() {
    return critical;
  }
}
