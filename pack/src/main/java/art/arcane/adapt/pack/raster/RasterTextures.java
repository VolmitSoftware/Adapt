package art.arcane.adapt.pack.raster;

import art.arcane.adapt.client.ui.UiResources;
import art.arcane.adapt.client.ui.UiTexture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Texture files by id ({@code adapt:textures/gui/theme/herbalism.png}), decoded once. The same files answer as
 * {@link UiResources} so the client's sprite cache can read them for the composer.
 */
public final class RasterTextures implements UiResources {
    private final Function<String, byte[]> files;
    private final Map<String, Raster> decoded = new HashMap<>();

    /** {@code files} gives a file's bytes by id, or null when there is none. */
    public RasterTextures(Function<String, byte[]> files) {
        this.files = Objects.requireNonNull(files, "files");
    }

    /** Files from a map of id to bytes. */
    public static RasterTextures of(Map<String, byte[]> files) {
        Map<String, byte[]> copy = Map.copyOf(files);
        return new RasterTextures(copy::get);
    }

    /** The texture decoded, or null when no file has its id. */
    public Raster get(UiTexture texture) {
        String id = texture.id();
        if (!decoded.containsKey(id)) {
            byte[] bytes = files.apply(id);
            decoded.put(id, bytes == null ? null : Raster.decode(bytes));
        }
        return decoded.get(id);
    }

    @Override
    public InputStream open(String id) throws IOException {
        byte[] bytes = files.apply(id);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    @Override
    public int revision() {
        return 0;
    }
}
