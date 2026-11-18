package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.util.common.math.PlayerInputPort;
import org.bukkit.Input;
import org.bukkit.entity.Player;

final class BukkitPlayerInputPort implements PlayerInputPort {
  private volatile boolean unsupported;

  @Override
  public PlayerInput currentInput(PlayerRef player) {
    Player bukkit = BukkitRefs.player(player);
    if (bukkit == null || unsupported) {
      return null;
    }
    try {
      Input input = bukkit.getCurrentInput();
      if (input == null) {
        return null;
      }
      return new PlayerInput(input.isForward(), input.isBackward(), input.isLeft(), input.isRight(), input.isJump(),
          input.isSneak(), input.isSprint());
    } catch (NoSuchMethodError missing) {
      unsupported = true;
      return null;
    }
  }
}
