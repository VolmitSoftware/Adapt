package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.CancellableEvent;

import java.util.Objects;

/** A projectile is launched. The shooter is {@code projectile().shooter()}. Mutable: cancelled. */
public class ProjectileLaunchEvent extends CancellableEvent {
  private final ProjectileRef projectile;

  public ProjectileLaunchEvent(ProjectileRef projectile) {
    this.projectile = Objects.requireNonNull(projectile, "projectile");
  }

  public ProjectileRef projectile() {
    return projectile;
  }
}
