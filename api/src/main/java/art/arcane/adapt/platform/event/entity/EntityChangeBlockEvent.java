package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An entity is about to change a block (falling blocks starting and landing, endermen, ravagers, sheep, copper golem
 * statues). Cancelling leaves the block as it is; a cancelled falling start also keeps the falling block entity from
 * being added.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityChangeBlockEvent extends CancellableEvent {
  private final EntityRef entity;
  private final BlockRef block;
  private final BlockStateRef to;

  public EntityChangeBlockEvent(EntityRef entity, BlockRef block, BlockStateRef to) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.block = Objects.requireNonNull(block, "block");
    this.to = Objects.requireNonNull(to, "to");
  }

  /** The entity making the change. */
  public EntityRef entity() {
    return entity;
  }

  /** The block being changed, still in its old state. */
  public BlockRef block() {
    return block;
  }

  /** The state the block changes to; air or the fluid left behind when the block is removed. */
  public BlockStateRef to() {
    return to;
  }
}
