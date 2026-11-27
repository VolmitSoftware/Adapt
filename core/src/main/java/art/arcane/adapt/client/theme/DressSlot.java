package art.arcane.adapt.client.theme;

/**
 * Where a dressing sprite goes on a book's edge: draped over the top band, hanging from it, down a side (drawn for the
 * left, mirrored on the right), over the foot, on a top or bottom corner, loose bits that are also the particles, and
 * for a mutated page the long reach across the spine and the hooks braided down the gutter, and the puffs a kit
 * with a trim billows over the page's inner edge.
 */
public enum DressSlot {
    TOP("top"),
    HANG("hang"),
    SIDE("side"),
    FOOT("foot"),
    CORNER("corner"),
    PILE("pile"),
    BIT("bit"),
    REACH("reach"),
    HOOK("hook"),
    TRIM("trim");

    private final String key;

    DressSlot(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
