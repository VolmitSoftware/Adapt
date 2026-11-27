package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/** Projectile (arrows, tridents, snowballs, pearls, potions and so on). */
@ApiStatus.AvailableSince("1.0.0")
public interface ProjectileRef extends EntityRef {
  /** Shooter if still loaded, else null. */
  EntityRef shooter();

  /** Shooter UUID even when the shooter is unloaded, or null. */
  UUID shooterId();

  /** Sets the entity credited with the shot; null clears it. */
  void setShooter(EntityRef shooter);

  /** Item the projectile represents (arrow stack, trident, thrown item); empty when none. */
  ItemRef item();

  /** Sets the item a thrown-item projectile (snowball, egg, potion and so on) shows and drops; ignored by other projectiles. */
  void setItem(ItemRef item);

  /** Arrow damage, or NaN for projectiles without a damage value. */
  double damage();

  /** Sets the arrow's base damage; ignored by projectiles without a damage value. */
  void setDamage(double damage);

  /** True for a critical arrow, the fully drawn shot that leaves a particle trail and hits harder. */
  boolean isCritical();

  /** Marks an arrow critical or not. */
  void setCritical(boolean critical);

  /** Arrow pickup allowed. */
  boolean canPickup();

  /** Allows or forbids picking the arrow up. */
  void setCanPickup(boolean pickup);

  /** Arrow pickup rule; {@link ArrowPickup#DISALLOWED} for projectiles that are not arrows. */
  ArrowPickup pickupRule();

  /** Sets the arrow pickup rule; ignored by projectiles that are not arrows. */
  void setPickupRule(ArrowPickup rule);

  /** Arrow pierce level (entities it passes through); 0 for projectiles that are not arrows. */
  int pierceLevel();

  /** Sets the arrow pierce level (clamped to 0..127); ignored by projectiles that are not arrows. */
  void setPierceLevel(int level);

  /** True while an arrow is stuck in a block. */
  boolean isInBlock();

  /** True when an arrow was fired from a crossbow. */
  boolean shotFromCrossbow();

  /** Turns the projectile to face {@code yaw}/{@code pitch} without moving it. */
  void setRotation(float yaw, float pitch);

  /** Base potion and custom effects of a tipped arrow; {@link PotionContents#EMPTY} for other projectiles. */
  PotionContents arrowPotion();

  /** Sets the base potion and custom effects of a tipped arrow (colour and name are ignored); ignored by other projectiles. */
  void setArrowPotion(PotionContents contents);

  /** Ticks of glowing a spectral arrow applies; 0 for other projectiles. */
  int glowingTicks();

  /** Sets the glowing ticks of a spectral arrow; ignored by other projectiles. */
  void setGlowingTicks(int ticks);

  /**
   * Detached copy of this projectile's replicable state (flags, flight properties, item, potion, persistent data),
   * safe to carry to another region. Null for projectile kinds that cannot be copied.
   */
  ProjectileSnapshot snapshot();
}
