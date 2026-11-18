package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Entity explosion. Mutable: the block list (remove to protect blocks), yield, cancelled. */
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

  public EntityRef entity() {
    return entity;
  }

  public Loc location() {
    return location;
  }

  public List<BlockRef> blocks() {
    return blocks;
  }

  public float yield() {
    return yield;
  }

  public void setYield(float yield) {
    this.yield = yield;
  }
}
