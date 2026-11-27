package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.AssetRef;

import java.util.List;

/** Where the server's art goes: it picks the files of a manifest to ask for and takes them as they arrive. */
public interface AssetSink {
    AssetSink NONE = new AssetSink() {
        @Override
        public List<String> manifest(List<AssetRef> assets) {
            return List.of();
        }

        @Override
        public void asset(String resource, String hash, byte[] data) {
        }

        @Override
        public void reset() {
        }
    };

    /** The resource ids of {@code assets} this client lacks and wants sent. */
    List<String> manifest(List<AssetRef> assets);

    void asset(String resource, String hash, byte[] data);

    /** Forgets every received file, as when the connection ends. */
    void reset();
}
