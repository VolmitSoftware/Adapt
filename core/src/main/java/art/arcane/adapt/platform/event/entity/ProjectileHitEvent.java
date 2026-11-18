package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** A projectile hit an entity or a block (exactly one of them is non-null). Mutable: cancelled. */
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

  public ProjectileRef projectile() {
    return projectile;
  }

  public EntityRef hitEntity() {
    return hitEntity;
  }

  public BlockRef hitBlock() {
    return hitBlock;
  }

  public BlockFace hitFace() {
    return hitFace;
  }
}
