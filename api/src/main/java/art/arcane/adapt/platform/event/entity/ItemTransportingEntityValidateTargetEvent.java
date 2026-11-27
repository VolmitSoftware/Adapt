package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * An item-transporting mob, such as a copper golem, checks whether a container may be its target. Not cancellable; set
 * {@link #setAllowed(boolean)} to false to reject the container. Mod loaders post it only for containers vanilla
 * already accepts.
 *
 * <p>On Bukkit servers only Paper 26.x posts it.
 */
@ApiStatus.AvailableSince("1.0.0")
public class ItemTransportingEntityValidateTargetEvent extends AdaptEvent {
  private final EntityRef entity;
  private final BlockRef block;
  private boolean allowed;

  public ItemTransportingEntityValidateTargetEvent(EntityRef entity, BlockRef block, boolean allowed) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.block = Objects.requireNonNull(block, "block");
    this.allowed = allowed;
  }

  /** The mob checking the container. */
  public EntityRef entity() {
    return entity;
  }

  /** The container block. */
  public BlockRef block() {
    return block;
  }

  /** Whether the mob may use the container. */
  public boolean allowed() {
    return allowed;
  }

  /** Allows or rejects the container. */
  public void setAllowed(boolean allowed) {
    this.allowed = allowed;
  }
}
