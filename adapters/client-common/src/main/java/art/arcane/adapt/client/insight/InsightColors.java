package art.arcane.adapt.client.insight;

/** Insight plate colours as RGB; {@link #alpha} adds the opacity. */
final class InsightColors {
    static final int TEXT = 0xF2F2F2;
    static final int DIM = 0xB4B9C0;
    static final int PANEL = 0x0E1116;
    static final int TRACK = 0x000000;
    static final int GHOST = 0xFFFFFF;
    static final int ABSORPTION = 0xF2C94C;
    static final int YOURS = 0x8FE08A;
    static final int HOSTILE = 0xE5534B;
    static final int PASSIVE = 0x7BC86C;
    static final int PLAYER = 0x5DADE2;
    static final int HARMFUL = 0xE5534B;
    static final int BENEFICIAL = 0x6FA8DC;
    static final double PANEL_OPACITY = 0.62D;
    static final double TRACK_OPACITY = 0.55D;

    private InsightColors() {
    }

    /** ARGB with {@code opacity} (0..1); 0 when too faint to draw, since the font treats tiny alphas as opaque. */
    static int alpha(int rgb, double opacity) {
        int alpha = (int) Math.round(Math.max(0D, Math.min(1D, opacity)) * 255D);
        return alpha < 8 ? 0 : alpha << 24 | rgb & 0xFFFFFF;
    }
}
