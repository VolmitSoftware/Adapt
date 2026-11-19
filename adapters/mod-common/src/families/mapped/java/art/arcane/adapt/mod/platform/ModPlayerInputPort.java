package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.util.common.math.PlayerInputPort;

/** Clients before 1.21.2 only send movement keys while steering a vehicle, so core infers input from velocity. */
public final class ModPlayerInputPort implements PlayerInputPort {
    @Override
    public PlayerInput currentInput(PlayerRef player) {
        return null;
    }
}
