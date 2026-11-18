package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** An item-transporting mob (copper golem) checks a container as a target. Mutable: allowed. */
public class ItemTransportingEntityValidateTargetEvent extends AdaptEvent {
  private final EntityRef entity;
  private final BlockRef block;
  private boolean allowed;

  public ItemTransportingEntityValidateTargetEvent(EntityRef entity, BlockRef block, boolean allowed) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.block = Objects.requireNonNull(block, "block");
    this.allowed = allowed;
  }

  public EntityRef entity() {
    return entity;
  }

  public BlockRef block() {
    return block;
  }

  public boolean allowed() {
    return allowed;
  }

  public void setAllowed(boolean allowed) {
    this.allowed = allowed;
  }
}
