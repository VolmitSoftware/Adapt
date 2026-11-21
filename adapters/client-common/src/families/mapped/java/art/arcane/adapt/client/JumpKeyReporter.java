package art.arcane.adapt.client;

import art.arcane.adapt.client.net.ClientNet;
import net.minecraft.client.Minecraft;

/**
 * Reports the jump key to servers before 1.21.2, which receive the player's movement keys only while riding. Runs at
 * the start of the client tick, so the report reaches the server ahead of the move the key belongs to, in the same
 * order as the player input packet of later versions.
 */
final class JumpKeyReporter {
    private final ClientNet net;
    private boolean reported;

    JumpKeyReporter(ClientNet net) {
        this.net = net;
    }

    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean down = minecraft.player != null && minecraft.screen == null && minecraft.options.keyJump.isDown();
        if (down != reported && net.jumpKey(down)) {
            reported = down;
        }
    }
}
