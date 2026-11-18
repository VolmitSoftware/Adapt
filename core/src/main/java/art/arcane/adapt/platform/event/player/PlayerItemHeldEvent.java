package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;

/** Hotbar selection change. Mutable: cancelled. */
public class PlayerItemHeldEvent extends PlayerEvent {
  private final int previousSlot;
  private final int newSlot;

  public PlayerItemHeldEvent(PlayerRef player, int previousSlot, int newSlot) {
    super(player);
    this.previousSlot = previousSlot;
    this.newSlot = newSlot;
  }

  public int previousSlot() {
    return previousSlot;
  }

  public int newSlot() {
    return newSlot;
  }
}
