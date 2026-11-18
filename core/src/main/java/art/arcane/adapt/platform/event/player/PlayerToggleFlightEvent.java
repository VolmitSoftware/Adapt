package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;

/** Player toggles flight. {@link #isFlying()} is the new state. Mutable: cancelled. */
public class PlayerToggleFlightEvent extends PlayerEvent {
  private final boolean flying;

  public PlayerToggleFlightEvent(PlayerRef player, boolean flying) {
    super(player);
    this.flying = flying;
  }

  public boolean isFlying() {
    return flying;
  }
}
