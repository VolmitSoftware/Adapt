package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An explosion caused by an entity, such as a creeper or primed TNT, is about to break blocks. Cancelling keeps every
 * block intact. Mutable: the block list (remove to protect blocks), yield, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityExplodeEvent extends CancellableEvent {
  private final EntityRef entity;
  private final Loc location;
  private final List<BlockRef> blocks;
  private float yield;

  public EntityExplodeEvent(EntityRef entity, Loc location, List<BlockRef> blocks, float yield) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.location = Objects.requireNonNull(location, "location");
    this.blocks = new ArrayList<>(blocks);
    this.yield = yield;
  }

  /** The entity behind the explosion. */
  public EntityRef entity() {
    return entity;
  }

  /** The centre of the explosion. */
  public Loc location() {
    return location;
  }

  /** Blocks the explosion will break, editable: remove a block to keep it, add one to break it too. */
  public List<BlockRef> blocks() {
    return blocks;
  }

  /** Fraction of the broken blocks that drop as items; 1 / radius unless a handler changed it. */
  public float yield() {
    return yield;
  }

  /** Changes the drop fraction; 1 lets every broken block drop. */
  public void setYield(float yield) {
    this.yield = yield;
  }
}
