package art.arcane.adapt.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link UiSprites} over the client's resource packs. Each id is read once (the PNG header, the slice JSON for nine-slices,
 * or the whole image when its luminance is asked for) and remembered, missing or not, until {@link #refresh()} sees the
 * packs reloaded. Render thread only.
 */
public final class UiSpriteCache implements UiSprites {
    private final UiResources resources;
    private final Map<String, UiSpriteLookup> lookups = new HashMap<>();
    private final Map<String, UiNineSlice> slices = new HashMap<>();
    private final Map<String, Float> luminances = new HashMap<>();
    private final Map<String, byte[]> files = new HashMap<>();
    private int revision;

    public UiSpriteCache(UiResources resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    /** Forgets every lookup when the resource packs were reloaded since the last call. Call once per client tick. */
    public void refresh() {
        int current = resources.revision();
        if (current != revision) {
            revision = current;
            lookups.clear();
            slices.clear();
            luminances.clear();
            files.clear();
        }
    }

    @Override
    public byte[] data(String id) {
        if (files.containsKey(id)) {
            return files.get(id);
        }
        byte[] read;
        try (InputStream stream = resources.open(id)) {
            read = stream == null ? null : stream.readNBytes(MAX_DATA_BYTES);
        } catch (IOException | RuntimeException error) {
            read = null;
        }
        files.put(id, read);
        return read;
    }

    @Override
    public UiSpriteLookup lookup(String id) {
        UiSpriteLookup cached = lookups.get(id);
        if (cached == null) {
            cached = load(id);
            lookups.put(id, cached);
        }
        return cached;
    }

    @Override
    public UiNineSlice nineSlice(String id) {
        if (slices.containsKey(id)) {
            return slices.get(id);
        }
        UiNineSlice slice = loadSlice(id);
        slices.put(id, slice);
        return slice;
    }

    @Override
    public float luminance(String id) {
        Float cached = luminances.get(id);
        if (cached == null) {
            cached = measure(id);
            luminances.put(id, cached);
        }
        return cached;
    }

    private float measure(String id) {
        try (InputStream stream = resources.open(id)) {
            return stream == null ? PngLuma.UNKNOWN : PngLuma.mean(stream.readNBytes(PngLuma.MAX_BYTES));
        } catch (IOException | RuntimeException error) {
            return PngLuma.UNKNOWN;
        }
    }

    private UiSpriteLookup load(String id) {
        try (InputStream stream = resources.open(id)) {
            if (stream == null) {
                return UiSpriteLookup.MISSING;
            }
            UiAssetCheck.Size size = UiAssetCheck.pngSize(stream.readNBytes(UiAssetCheck.HEADER_BYTES));
            if (size == null) {
                return UiSpriteLookup.invalid("not a PNG");
            }
            String relative = UiAssetCheck.relative(id);
            String problem = relative == null ? null : UiAssetCheck.sizeProblem(relative, size.width(), size.height());
            return problem != null ? UiSpriteLookup.invalid(problem) : UiSpriteLookup.found(new UiTexture(id, size.width(), size.height()));
        } catch (IOException | RuntimeException error) {
            return UiSpriteLookup.invalid("unreadable: " + error.getMessage());
        }
    }

    private UiNineSlice loadSlice(String id) {
        UiTexture texture = texture(id);
        if (texture == null) {
            return null;
        }
        try (InputStream stream = resources.open(UiAssetCheck.sliceId(id))) {
            if (stream == null) {
                return null;
            }
            return UiAssetCheck.parseSlice(texture, new String(stream.readNBytes(UiAssetCheck.MAX_SLICE_JSON), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }
}
