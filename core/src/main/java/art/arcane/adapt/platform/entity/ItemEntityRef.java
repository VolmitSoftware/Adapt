package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.item.ItemRef;

import java.util.UUID;

/** Dropped item entity. */
public interface ItemEntityRef extends EntityRef {
  ItemRef item();

  void setItem(ItemRef item);

  int pickupDelay();

  void setPickupDelay(int ticks);

  UUID thrower();

  void setThrower(UUID thrower);

  UUID owner();

  void setOwner(UUID owner);

  /**
   * False when the server forbids players picking this item up (Paper flag; on modded an entity tag Adapt enforces,
   * or vanilla's never-pick-up delay that display items use).
   */
  boolean canPlayerPickup();

  /** False when the server forbids mobs picking this item up (Paper flag; on modded an entity tag Adapt enforces). */
  boolean canMobPickup();

  void setCanMobPickup(boolean pickup);

  /** True keeps the item from ever despawning; false restores the normal five-minute lifetime. */
  void setUnlimitedLifetime(boolean unlimited);
}
