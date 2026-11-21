package art.arcane.adapt.client.net;

/** The loader's client half of the {@code adapt:ui} payload. Called on the client thread. */
public interface ClientTransport {
    /** True once the server registered {@code adapt:ui} on this connection, so a frame can be sent. */
    boolean ready();

    void send(byte[] frame);
}
