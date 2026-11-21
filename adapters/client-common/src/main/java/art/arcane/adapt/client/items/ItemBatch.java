package art.arcane.adapt.client.items;

import art.arcane.adapt.client.protocol.ItemDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collects the chunks of one item list. The server sends non-empty {@code CustomItems} frames and ends the list with an
 * empty one; that empty frame completes the list, so an empty frame on its own is an empty list.
 */
final class ItemBatch {
    private final List<ItemDescriptor> pending = new ArrayList<>();

    /** Adds a chunk; returns the whole list when {@code chunk} is the terminating empty frame. */
    synchronized Optional<List<ItemDescriptor>> accept(List<ItemDescriptor> chunk) {
        if (!chunk.isEmpty()) {
            pending.addAll(chunk);
            return Optional.empty();
        }
        List<ItemDescriptor> complete = List.copyOf(pending);
        pending.clear();
        return Optional.of(complete);
    }

    synchronized void clear() {
        pending.clear();
    }
}
