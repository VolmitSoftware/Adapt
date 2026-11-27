package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/** Dropped item entity. */
@ApiStatus.AvailableSince("1.0.0")
public interface ItemEntityRef extends EntityRef {
  /** The stack this entity carries. */
  ItemRef item();

  /** Replaces the carried stack. */
  void setItem(ItemRef item);

  /** Ticks before the item can be picked up. */
  int pickupDelay();

  /** Sets the ticks before the item can be picked up. */
  void setPickupDelay(int ticks);

  /** Player or entity that dropped the item, or null. */
  UUID thrower();

  /** Sets who dropped the item; null clears it. */
  void setThrower(UUID thrower);

  /** The only entity allowed to pick the item up, or null when anyone may. */
  UUID owner();

  /** Restricts pickup to {@code owner}; null lets anyone pick it up. */
  void setOwner(UUID owner);

  /**
   * False when the server forbids players picking this item up (Paper flag; on modded an entity tag Adapt enforces,
   * or vanilla's never-pick-up delay that display items use).
   */
  boolean canPlayerPickup();

  /** False when the server forbids mobs picking this item up (Paper flag; on modded an entity tag Adapt enforces). */
  boolean canMobPickup();

  /** Allows or forbids mobs picking the item up. */
  void setCanMobPickup(boolean pickup);

  /** True keeps the item from ever despawning; false restores the normal five-minute lifetime. */
  void setUnlimitedLifetime(boolean unlimited);
}
