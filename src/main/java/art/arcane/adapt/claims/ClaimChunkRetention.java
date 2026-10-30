package art.arcane.adapt.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.PluginChunkTicket;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ClaimChunkRetention implements AutoCloseable {
    private final Map<Key, PluginChunkTicket> tickets = new HashMap<>();
    private boolean closed;

    CompletableFuture<Void> load(World world, int x, int z) {
        synchronized (this) {
            if (closed || tickets.containsKey(new Key(world, x, z)) && world.isChunkLoaded(x, z)) {
                return CompletableFuture.completedFuture(null);
            }
        }
        return world.getChunkAtAsync(x, z, true).thenAccept(chunk -> {
            if (chunk == null) {
                throw new IllegalStateException("Chunk did not load at " + world.getName() + " " + x + "," + z);
            }
            retain(new Key(world, x, z));
        });
    }

    void copyTo(ClaimChunkRetention destination) {
        Map<Key, PluginChunkTicket> snapshot;
        synchronized (this) {
            snapshot = Map.copyOf(tickets);
        }
        for (Key key : snapshot.keySet()) {
            destination.retain(key);
        }
    }

    @Override
    public void close() {
        Map<Key, PluginChunkTicket> released;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            released = new HashMap<>(tickets);
            tickets.clear();
        }
        for (Map.Entry<Key, PluginChunkTicket> entry : released.entrySet()) {
            Location anchor = entry.getKey().anchor();
            if (J.isOwnedByCurrentRegion(anchor)) {
                release(entry.getValue());
            } else if (!J.runAt(anchor, () -> release(entry.getValue()))) {
                Adapt.error(new IllegalStateException("Could not release claim travel chunk ticket at " + anchor));
            }
        }
    }

    private void release(PluginChunkTicket ticket) {
        try {
            ticket.close();
        } catch (RuntimeException | LinkageError failure) {
            Adapt.error(failure);
        }
    }

    private synchronized void retain(Key key) {
        if (!closed && !tickets.containsKey(key)) {
            tickets.put(key, PluginChunkTicket.acquire(Adapt.instance, key.anchor()));
        }
    }

    private record Key(World world, int x, int z) {
        private Location anchor() {
            return new Location(world, x << 4, 0, z << 4);
        }
    }
}
