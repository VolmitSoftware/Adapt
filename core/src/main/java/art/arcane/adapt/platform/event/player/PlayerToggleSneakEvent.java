package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;

/** Player toggles sneak. {@link #isSneaking()} is the new state. Mutable: cancelled. */
public class PlayerToggleSneakEvent extends PlayerEvent {
  private final boolean sneaking;

  public PlayerToggleSneakEvent(PlayerRef player, boolean sneaking) {
    super(player);
    this.sneaking = sneaking;
  }

  public boolean isSneaking() {
    return sneaking;
  }
}
