package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;

/** Player toggles sprint. {@link #isSprinting()} is the new state. Mutable: cancelled. */
public class PlayerToggleSprintEvent extends PlayerEvent {
  private final boolean sprinting;

  public PlayerToggleSprintEvent(PlayerRef player, boolean sprinting) {
    super(player);
    this.sprinting = sprinting;
  }

  public boolean isSprinting() {
    return sprinting;
  }
}
