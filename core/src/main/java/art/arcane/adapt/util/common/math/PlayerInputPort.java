package art.arcane.adapt.util.common.math;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;

/**
 * Reads the movement keys a player's client last reported. Installed through {@link art.arcane.adapt.AdaptPorts};
 * {@link #NONE} reports nothing, and {@link VelocitySpeed} then infers input from velocity.
 */
public interface PlayerInputPort {
  PlayerInputPort NONE = player -> null;

  /** Current input, or null when the platform cannot read it. */
  PlayerInput currentInput(PlayerRef player);
}
