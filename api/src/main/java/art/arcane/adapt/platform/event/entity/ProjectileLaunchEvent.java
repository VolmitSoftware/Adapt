package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A projectile is about to be added to the world. Cancelling keeps it out. The shooter is
 * {@code projectile().shooter()}. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class ProjectileLaunchEvent extends CancellableEvent {
  private final ProjectileRef projectile;

  public ProjectileLaunchEvent(ProjectileRef projectile) {
    this.projectile = Objects.requireNonNull(projectile, "projectile");
  }

  /** The projectile being launched. */
  public ProjectileRef projectile() {
    return projectile;
  }
}
