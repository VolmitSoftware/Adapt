package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.ItemDescriptor;

import java.util.List;

/** Receives the server's custom item list for recipe viewers. Called on the client thread. */
public interface ItemSink {
    ItemSink NONE = new ItemSink() {
        @Override
        public void items(List<ItemDescriptor> items) {
        }

        @Override
        public void reset() {
        }
    };

    void items(List<ItemDescriptor> items);

    /** The session ended: forget the server's items. */
    void reset();
}
