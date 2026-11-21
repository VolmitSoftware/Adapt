package art.arcane.adapt.client.ui;

/**
 * What a sprite id resolved to: a usable texture, nothing (no pack has the file), or a file that cannot be used and
 * why.
 */
public record UiSpriteLookup(UiTexture texture, String problem) {
    public static final UiSpriteLookup MISSING = new UiSpriteLookup(null, null);

    public static UiSpriteLookup found(UiTexture texture) {
        return new UiSpriteLookup(texture, null);
    }

    public static UiSpriteLookup invalid(String problem) {
        return new UiSpriteLookup(null, problem);
    }

    public boolean usable() {
        return texture != null;
    }
}
