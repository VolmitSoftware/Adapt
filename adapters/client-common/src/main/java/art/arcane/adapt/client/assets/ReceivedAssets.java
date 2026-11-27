package art.arcane.adapt.client.assets;

import art.arcane.adapt.engine.asset.AssetHashes;
import art.arcane.adapt.client.net.AssetSink;
import art.arcane.adapt.client.protocol.AssetRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Art the server sent this connection, served by resource id as if a resource pack held it: textures are registered
 * with the game under their id and every file is readable through {@link #bytes(String)}. Files a pack or the mod
 * already holds are never asked for; everything is dropped when the connection ends. Client thread only.
 */
public final class ReceivedAssets implements AssetSink {
    public static final ReceivedAssets SHARED = new ReceivedAssets();
    private static final Logger LOGGER = LoggerFactory.getLogger("Adapt");
    private static final String TEXTURES = "textures/";

    private final Map<String, Received> files = new HashMap<>();
    private final Map<String, String> pending = new HashMap<>();
    private AssetTextures textures = AssetTextures.NONE;
    private int revision;

    private record Received(String hash, byte[] bytes, boolean texture) {
    }

    /** Sets the game's textures; call once when the client starts. */
    public void textures(AssetTextures installed) {
        textures = Objects.requireNonNull(installed, "installed");
    }

    /** Changes whenever a file arrives or the files are dropped, so cached lookups know to read again. */
    public int revision() {
        return revision;
    }

    /** The received file {@code id}, or null. */
    public byte[] bytes(String id) {
        Received received = files.get(id);
        return received == null ? null : received.bytes();
    }

    @Override
    public List<String> manifest(List<AssetRef> assets) {
        pending.clear();
        List<String> wanted = new ArrayList<>();
        for (AssetRef asset : assets) {
            Received held = files.get(asset.resource());
            if (held != null ? held.hash().equals(asset.hash()) : textures.local(asset.resource())) {
                continue;
            }
            pending.put(asset.resource(), asset.hash());
            wanted.add(asset.resource());
        }
        return List.copyOf(wanted);
    }

    @Override
    public void asset(String resource, String hash, byte[] data) {
        String expected = pending.remove(resource);
        if (expected == null || !expected.equals(hash) || !hash.equals(AssetHashes.sha1(data))) {
            LOGGER.warn("Adapt client ignored asset {}: it was not asked for or does not match its hash", resource);
            return;
        }
        Received previous = files.remove(resource);
        if (previous != null && previous.texture()) {
            textures.release(resource);
        }
        boolean texture = isTexture(resource) && textures.register(resource, data);
        files.put(resource, new Received(hash, data, texture));
        revision++;
    }

    @Override
    public void reset() {
        pending.clear();
        if (files.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Received> file : files.entrySet()) {
            if (file.getValue().texture()) {
                textures.release(file.getKey());
            }
        }
        files.clear();
        revision++;
    }

    private static boolean isTexture(String resource) {
        int colon = resource.indexOf(':');
        return resource.startsWith(TEXTURES, colon + 1) && resource.endsWith(".png");
    }
}
