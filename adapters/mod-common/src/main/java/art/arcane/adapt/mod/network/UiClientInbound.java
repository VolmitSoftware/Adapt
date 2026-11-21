package art.arcane.adapt.mod.network;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Where the loaders hand clientbound {@code adapt:ui} frames. The client entry point sets the handler; on a dedicated
 * server it stays a no-op, so loader code reaches the client without naming a client class.
 */
public final class UiClientInbound {
    private static volatile Consumer<byte[]> handler = (byte[] frame) -> {
    };

    private UiClientInbound() {
    }

    public static void handler(Consumer<byte[]> receiver) {
        handler = Objects.requireNonNull(receiver, "receiver");
    }

    public static void accept(byte[] frame) {
        handler.accept(frame);
    }
}
