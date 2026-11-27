package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/** A player selects a different hotbar slot. Cancelling keeps the current slot. Mutable: cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerItemHeldEvent extends PlayerEvent {
  private final int previousSlot;
  private final int newSlot;

  public PlayerItemHeldEvent(PlayerRef player, int previousSlot, int newSlot) {
    super(player);
    this.previousSlot = previousSlot;
    this.newSlot = newSlot;
  }

  /** Hotbar slot selected before, 0 to 8. */
  public int previousSlot() {
    return previousSlot;
  }

  /** Hotbar slot being selected, 0 to 8. */
  public int newSlot() {
    return newSlot;
  }
}
