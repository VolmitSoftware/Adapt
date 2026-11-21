package art.arcane.adapt.client.ui.kit;

/**
 * Screen size classes by scaled size. Minecraft picks the GUI scale so the scaled screen is at least 320x240 unless
 * the window itself is smaller, so COMPACT covers the largest GUI scale on small windows.
 */
public enum UiBreakpoint {
    COMPACT,
    REGULAR,
    WIDE;

    public static final int REGULAR_WIDTH = 400;
    public static final int REGULAR_HEIGHT = 260;
    public static final int WIDE_WIDTH = 640;
    public static final int WIDE_HEIGHT = 360;

    public static UiBreakpoint of(int width, int height) {
        if (width < REGULAR_WIDTH || height < REGULAR_HEIGHT) {
            return COMPACT;
        }
        if (width >= WIDE_WIDTH && height >= WIDE_HEIGHT) {
            return WIDE;
        }
        return REGULAR;
    }

    public boolean atLeast(UiBreakpoint other) {
        return ordinal() >= other.ordinal();
    }
}
