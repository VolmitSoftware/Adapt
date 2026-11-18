package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;

/**
 * A lingering potion breaks and creates its cloud. Removing the cloud entity is allowed. Mutable:
 * cancelled.
 */
public class LingeringPotionSplashEvent extends CancellableEvent {
  private final ProjectileRef potion;
  private final ItemRef item;
  private final EntityRef cloud;

  public LingeringPotionSplashEvent(ProjectileRef potion, ItemRef item, EntityRef cloud) {
    this.potion = Objects.requireNonNull(potion, "potion");
    this.item = Objects.requireNonNull(item, "item");
    this.cloud = Objects.requireNonNull(cloud, "cloud");
  }

  public ProjectileRef potion() {
    return potion;
  }

  public ItemRef item() {
    return item;
  }

  public EntityRef cloud() {
    return cloud;
  }
}
