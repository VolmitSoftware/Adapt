package art.arcane.adapt.client.state;

import art.arcane.adapt.client.protocol.ClientFrame;

/** Encodes and sends one frame; false when the frame could not go out. */
@FunctionalInterface
public interface ClientFrameSender {
    boolean send(ClientFrame frame);
}
