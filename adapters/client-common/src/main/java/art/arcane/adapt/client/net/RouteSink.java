package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.Route;

/** Opens the designed screen the server asked for. Called on the client thread. */
public interface RouteSink {
    RouteSink NONE = (Route route) -> {
    };

    void open(Route route);
}
