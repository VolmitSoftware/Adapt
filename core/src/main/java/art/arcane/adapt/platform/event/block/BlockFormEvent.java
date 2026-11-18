package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Objects;

/**
 * A block forms from the world (ice, snow, obsidian, concrete) or from an entity (snow golem trail,
 * frost walker); entity() is null for world forms. Mutable: cancelled.
 */
public class BlockFormEvent extends CancellableEvent {
  private final BlockRef block;
  private final BlockStateRef newState;
  private final EntityRef entity;

  public BlockFormEvent(BlockRef block, BlockStateRef newState, EntityRef entity) {
    this.block = Objects.requireNonNull(block, "block");
    this.newState = Objects.requireNonNull(newState, "newState");
    this.entity = entity;
  }

  public BlockRef block() {
    return block;
  }

  public BlockStateRef newState() {
    return newState;
  }

  public EntityRef entity() {
    return entity;
  }
}
