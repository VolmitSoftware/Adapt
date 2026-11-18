package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.UUID;

/** Dropped item entity. */
public final class FakeItemEntity extends FakeEntity implements ItemEntityRef {
  private boolean canMobPickup = true;
  private boolean unlimitedLifetime;
  private ItemRef item;
  private int pickupDelay;
  private UUID thrower;
  private UUID owner;

  public FakeItemEntity(Loc location, ItemRef item) {
    super(Id.minecraft("item"), location);
    this.item = item;
  }

  @Override
  public ItemRef item() {
    return item;
  }

  @Override
  public void setItem(ItemRef item) {
    this.item = item;
  }

  @Override
  public int pickupDelay() {
    return pickupDelay;
  }

  @Override
  public void setPickupDelay(int ticks) {
    pickupDelay = ticks;
  }

  @Override
  public UUID thrower() {
    return thrower;
  }

  @Override
  public void setThrower(UUID thrower) {
    this.thrower = thrower;
  }

  @Override
  public UUID owner() {
    return owner;
  }

  @Override
  public void setOwner(UUID owner) {
    this.owner = owner;
  }

  @Override
  public boolean canPlayerPickup() {
    return true;
  }

  @Override
  public boolean canMobPickup() {
    return canMobPickup;
  }

  @Override
  public void setCanMobPickup(boolean pickup) {
    canMobPickup = pickup;
  }

  @Override
  public void setUnlimitedLifetime(boolean unlimited) {
    unlimitedLifetime = unlimited;
  }

  public boolean unlimitedLifetime() {
    return unlimitedLifetime;
  }
}
