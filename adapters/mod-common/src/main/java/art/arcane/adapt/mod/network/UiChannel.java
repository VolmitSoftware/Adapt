package art.arcane.adapt.mod.network;

import net.minecraft.server.level.ServerPlayer;

/** The loader's server half of the optional {@code adapt:ui} payload. */
public interface UiChannel {
    UiChannel NONE = new UiChannel() {
        @Override
        public boolean open(ServerPlayer player) {
            return false;
        }

        @Override
        public void send(ServerPlayer player, byte[] frame) {
        }
    };

    /** True when the player's client registered {@code adapt:ui}. */
    boolean open(ServerPlayer player);

    /** Queues one frame on the player's connection, in order with the vanilla packets queued around it. */
    void send(ServerPlayer player, byte[] frame);
}
