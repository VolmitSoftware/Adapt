package art.arcane.adapt.client.ui.kit;

/**
 * Screen size classes by scaled size. Minecraft picks the GUI scale so the scaled screen is at least 320x240 unless
 * the window itself is smaller, so COMPACT covers the largest GUI scale on small windows. The thresholds sit clear of
 * the scaled sizes common windows give (427x240, 480x270, 640x360, 815x458, 960x540), so those always land in the same
 * class however the window got there.
 */
public enum UiBreakpoint {
    /** Small: one page, rows at their tightest, no extra margins. */
    COMPACT,
    /** Medium: facing pages when both fit, rows may breathe, narrow margins. */
    REGULAR,
    /** Large: facing pages with bounded, generous margins. */
    WIDE;

    public static final int REGULAR_WIDTH = 400;
    public static final int REGULAR_HEIGHT = 250;
    public static final int WIDE_WIDTH = 600;
    public static final int WIDE_HEIGHT = 340;
    /** How far past a threshold a resize must go before the class changes, so a drag across it does not flicker. */
    public static final int HYSTERESIS = 8;

    public static UiBreakpoint of(int width, int height) {
        if (width < REGULAR_WIDTH || height < REGULAR_HEIGHT) {
            return COMPACT;
        }
        if (width >= WIDE_WIDTH && height >= WIDE_HEIGHT) {
            return WIDE;
        }
        return REGULAR;
    }

    /** The class after a resize from {@code previous} (null when the screen just opened). */
    public static UiBreakpoint settle(UiBreakpoint previous, int width, int height) {
        if (previous == null) {
            return of(width, height);
        }
        UiBreakpoint grown = of(width - HYSTERESIS, height - HYSTERESIS);
        if (grown.ordinal() > previous.ordinal()) {
            return grown;
        }
        UiBreakpoint shrunk = of(width + HYSTERESIS, height + HYSTERESIS);
        if (shrunk.ordinal() < previous.ordinal()) {
            return shrunk;
        }
        return previous;
    }

    public boolean atLeast(UiBreakpoint other) {
        return ordinal() >= other.ordinal();
    }
}
