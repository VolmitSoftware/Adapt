package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Objects;

/**
 * An entity changes a block (falling blocks starting and landing, endermen, ravagers, sheep, copper
 * golem statues). Mutable: cancelled.
 */
public class EntityChangeBlockEvent extends CancellableEvent {
  private final EntityRef entity;
  private final BlockRef block;
  private final BlockStateRef to;

  public EntityChangeBlockEvent(EntityRef entity, BlockRef block, BlockStateRef to) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.block = Objects.requireNonNull(block, "block");
    this.to = Objects.requireNonNull(to, "to");
  }

  public EntityRef entity() {
    return entity;
  }

  public BlockRef block() {
    return block;
  }

  public BlockStateRef to() {
    return to;
  }
}
