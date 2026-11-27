package art.arcane.adapt.platform.event.block;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A sculk sensor or similar listener receives a game event (vibration). entity() is the source, or
 * null. Mutable: cancelled (the listener ignores the vibration).
 */
@ApiStatus.AvailableSince("1.0.0")
public class BlockReceiveGameEvent extends CancellableEvent {
  private final BlockRef block;
  private final Id gameEvent;
  private final EntityRef entity;

  public BlockReceiveGameEvent(BlockRef block, Id gameEvent, EntityRef entity) {
    this.block = Objects.requireNonNull(block, "block");
    this.gameEvent = Objects.requireNonNull(gameEvent, "gameEvent");
    this.entity = entity;
  }

  /** The sculk sensor or other listening block. */
  public BlockRef block() {
    return block;
  }

  /** Id of the game event, such as {@code minecraft:step}. */
  public Id gameEvent() {
    return gameEvent;
  }

  /** The entity that caused the game event, or null. */
  public EntityRef entity() {
    return entity;
  }
}
