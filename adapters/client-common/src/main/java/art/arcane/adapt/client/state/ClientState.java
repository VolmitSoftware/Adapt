package art.arcane.adapt.client.state;

import art.arcane.adapt.client.protocol.ItemDescriptor;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViewPatch;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * What the client knows about the current server session: the negotiated version and capability bits, the catalog,
 * the player's view and the custom items. Read and written on the client thread; listeners run there too.
 */
public final class ClientState {
    private final List<Consumer<ServerInfo>> connectedListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> disconnectedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CatalogView>> catalogListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PlayerView>> playerListeners = new CopyOnWriteArrayList<>();
    private int version;
    private ServerInfo server;
    private CatalogView catalog;
    private PlayerView player;
    private List<ItemDescriptor> items = List.of();

    /** True between the server's {@code Accept} and the end of the session. */
    public boolean negotiated() {
        return server != null;
    }

    /** The negotiated protocol version, 0 when not negotiated. */
    public int version() {
        return version;
    }

    /** The negotiated capability bits, 0 when not negotiated. */
    public int capabilityMask() {
        return server == null ? 0 : server.capabilityMask();
    }

    public boolean has(int capabilityBit) {
        return (capabilityMask() & capabilityBit) == capabilityBit && capabilityBit != 0;
    }

    /** The server as it answered {@code Hello}, with the negotiated mask; null when not negotiated. */
    public ServerInfo server() {
        return server;
    }

    /** The latest catalog, or null before the server sent one. */
    public CatalogView catalog() {
        return catalog;
    }

    /** The player's current view, or null before the server sent one. */
    public PlayerView player() {
        return player;
    }

    public List<ItemDescriptor> items() {
        return items;
    }

    public ClientSubscription onConnected(Consumer<ServerInfo> listener) {
        return add(connectedListeners, listener);
    }

    public ClientSubscription onDisconnected(Runnable listener) {
        return add(disconnectedListeners, listener);
    }

    public ClientSubscription onCatalog(Consumer<CatalogView> listener) {
        return add(catalogListeners, listener);
    }

    public ClientSubscription onPlayer(Consumer<PlayerView> listener) {
        return add(playerListeners, listener);
    }

    /** The server accepted {@code Hello}; a renegotiation keeps the catalog and player view. */
    public void negotiated(int negotiatedVersion, ServerInfo info) {
        version = negotiatedVersion;
        server = Objects.requireNonNull(info, "info");
        for (Consumer<ServerInfo> listener : connectedListeners) {
            listener.accept(info);
        }
    }

    public void acceptCatalog(CatalogView view) {
        catalog = Objects.requireNonNull(view, "view");
        for (Consumer<CatalogView> listener : catalogListeners) {
            listener.accept(view);
        }
    }

    public void acceptPlayer(PlayerView view) {
        player = Objects.requireNonNull(view, "view");
        for (Consumer<PlayerView> listener : playerListeners) {
            listener.accept(view);
        }
    }

    /** Applies a delta to the current view; false (and nothing changes) before the first full view. */
    public boolean applyDelta(PlayerDelta delta) {
        PlayerView current = player;
        if (current == null) {
            return false;
        }
        acceptPlayer(PlayerViewPatch.apply(current, delta));
        return true;
    }

    public void acceptItems(List<ItemDescriptor> descriptors) {
        items = List.copyOf(descriptors);
    }

    /** Forgets the session; disconnected listeners run only if a session was negotiated. */
    public void reset() {
        boolean wasNegotiated = server != null;
        version = 0;
        server = null;
        catalog = null;
        player = null;
        items = List.of();
        if (wasNegotiated) {
            for (Runnable listener : disconnectedListeners) {
                listener.run();
            }
        }
    }

    private static <T> ClientSubscription add(List<T> listeners, T listener) {
        T added = Objects.requireNonNull(listener, "listener");
        listeners.add(added);
        return () -> listeners.remove(added);
    }
}
