package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.util.common.math.PlayerInputPort;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;

/** Reads the movement keys from the last client input packet. */
public final class ModPlayerInputPort implements PlayerInputPort {
    @Override
    public PlayerInput currentInput(PlayerRef player) {
        ServerPlayer handle = ModRefs.player(player);
        if (handle == null) {
            return null;
        }
        Input input = handle.getLastClientInput();
        return new PlayerInput(input.forward(), input.backward(), input.left(), input.right(), input.jump(), input.shift(),
            input.sprint());
    }
}
