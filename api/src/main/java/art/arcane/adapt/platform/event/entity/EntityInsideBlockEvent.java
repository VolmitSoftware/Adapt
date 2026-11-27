package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An entity's box is inside a block whose inside behaviour is about to run (pressure plates, powder snow, cobwebs,
 * fire). Cancelling skips that behaviour. Never posted for air.
 *
 * <p>On Bukkit servers only Paper posts it.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityInsideBlockEvent extends CancellableEvent {
  private final EntityRef entity;
  private final BlockRef block;

  public EntityInsideBlockEvent(EntityRef entity, BlockRef block) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.block = Objects.requireNonNull(block, "block");
  }

  /** The entity inside the block. */
  public EntityRef entity() {
    return entity;
  }

  /** The block whose inside behaviour is about to run. */
  public BlockRef block() {
    return block;
  }
}
