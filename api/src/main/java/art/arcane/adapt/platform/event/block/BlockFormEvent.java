package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A block forms from the world (ice, snow, obsidian, concrete) or from an entity (snow golem trail,
 * frost walker); entity() is null for world forms. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class BlockFormEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockStateRef newState;
  private final EntityRef entity;

  public BlockFormEvent(BlockRef block, BlockStateRef newState, EntityRef entity) {
    this.block = Objects.requireNonNull(block, "block");
    this.newState = Objects.requireNonNull(newState, "newState");
    this.entity = entity;
  }

  /** The block that forms. */
  public BlockRef block() {
    return block;
  }

  /** The state it forms into. */
  public BlockStateRef newState() {
    return newState;
  }

  /** The entity that formed it, or null when the world did. */
  public EntityRef entity() {
    return entity;
  }
}
