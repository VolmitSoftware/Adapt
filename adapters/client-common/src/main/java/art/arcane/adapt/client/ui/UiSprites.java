package art.arcane.adapt.client.ui;

/**
 * Texture files the client's resource packs provide, by id such as {@code adapt:textures/gui/skill/agility.png}. Lookups
 * are cheap enough to make every frame.
 */
public interface UiSprites {
    int MAX_DATA_BYTES = 1 << 20;

    UiSprites NONE = new UiSprites() {
        @Override
        public UiSpriteLookup lookup(String id) {
            return UiSpriteLookup.MISSING;
        }

        @Override
        public UiNineSlice nineSlice(String id) {
            return null;
        }
    };

    UiSpriteLookup lookup(String id);

    /**
     * The nine-slice texture {@code id} with the borders from its {@code .json} next to it, or null when either file is
     * missing or invalid.
     */
    UiNineSlice nineSlice(String id);

    /** The texture with its pixel size, or null when no pack provides a usable file. */
    default UiTexture texture(String id) {
        return id == null ? null : lookup(id).texture();
    }

    default boolean exists(String id) {
        return texture(id) != null;
    }

    /** The mean luminance of the texture's opaque pixels (see {@link PngLuma}), or {@link PngLuma#UNKNOWN}. */
    default float luminance(String id) {
        return PngLuma.UNKNOWN;
    }

    /**
     * The whole file {@code id} (at most {@link #MAX_DATA_BYTES}) from the top-most pack that has it, or null. The same
     * array is returned until the packs reload, so callers may key parsed data on it.
     */
    default byte[] data(String id) {
        return null;
    }
}
