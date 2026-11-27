package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A lingering potion breaks and is about to add its effect cloud. Cancelling keeps the cloud out of the world. Removing
 * the cloud entity is allowed. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class LingeringPotionSplashEvent extends CancellableEvent {
  private final ProjectileRef potion;
  private final ItemRef item;
  private final EntityRef cloud;

  public LingeringPotionSplashEvent(ProjectileRef potion, ItemRef item, EntityRef cloud) {
    this.potion = Objects.requireNonNull(potion, "potion");
    this.item = Objects.requireNonNull(item, "item");
    this.cloud = Objects.requireNonNull(cloud, "cloud");
  }

  /** The thrown potion. */
  public ProjectileRef potion() {
    return potion;
  }

  /** The potion item; empty when the platform has none. */
  public ItemRef item() {
    return item;
  }

  /** The effect cloud about to be added. */
  public EntityRef cloud() {
    return cloud;
  }
}
