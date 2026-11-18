package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/**
 * An entity's box is inside a block whose inside behaviour is about to run (pressure plates, powder
 * snow, cobwebs, fire). Mutable: cancelled.
 */
public class EntityInsideBlockEvent extends CancellableEvent {
  private final EntityRef entity;
  private final BlockRef block;

  public EntityInsideBlockEvent(EntityRef entity, BlockRef block) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.block = Objects.requireNonNull(block, "block");
  }

  public EntityRef entity() {
    return entity;
  }

  public BlockRef block() {
    return block;
  }
}
