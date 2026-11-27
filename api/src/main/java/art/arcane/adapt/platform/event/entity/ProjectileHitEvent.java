package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A projectile hit an entity or a block (exactly one of them is non-null). Cancelling lets the projectile pass as if
 * nothing was hit. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class ProjectileHitEvent extends CancellableEvent {
  private final ProjectileRef projectile;
  private final EntityRef hitEntity;
  private final BlockRef hitBlock;
  private final BlockFace hitFace;

  public ProjectileHitEvent(ProjectileRef projectile, EntityRef hitEntity, BlockRef hitBlock, BlockFace hitFace) {
    this.projectile = Objects.requireNonNull(projectile, "projectile");
    this.hitEntity = hitEntity;
    this.hitBlock = hitBlock;
    this.hitFace = hitFace;
  }

  /** The projectile that hit. */
  public ProjectileRef projectile() {
    return projectile;
  }

  /** The entity hit, or null for a block hit. */
  public EntityRef hitEntity() {
    return hitEntity;
  }

  /** The block hit, or null for an entity hit. */
  public BlockRef hitBlock() {
    return hitBlock;
  }

  /** The face of the block that was hit, or null for an entity hit. */
  public BlockFace hitFace() {
    return hitFace;
  }
}
