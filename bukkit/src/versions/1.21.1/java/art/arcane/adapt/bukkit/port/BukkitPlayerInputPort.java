package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.util.common.math.PlayerInputPort;

/** Paper before 26.x does not expose the client's movement keys. */
final class BukkitPlayerInputPort implements PlayerInputPort {
  @Override
  public PlayerInput currentInput(PlayerRef player) {
    return null;
  }
}
